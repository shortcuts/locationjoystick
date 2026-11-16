package com.locationjoystick.app.privacy

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.arsc.chunk.xml.ResXmlElement
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.regex.Pattern

internal fun rewriteApk(
    source: File,
    oldPkg: String,
    record: CloneRecord,
    out: File,
) {
    val apk = ApkModule.loadApkFile(source)
    apk.packageName = record.packageId
    val manifest = apk.androidManifest

    // Only provider authorities and permission names derive from the package; other names are dex classes.
    manifest.applicationElement.listElements("provider").forEach { provider ->
        rename(provider as ResXmlElement, "authorities") { renameAuthorities(it, oldPkg, record.packageId) }
    }
    listOf("permission", "uses-permission", "uses-permission-sdk-23").forEach { tag ->
        manifest.manifestElement.listElements(tag).forEach { permission ->
            rename(permission as ResXmlElement, "name") { renameWithPackage(it, oldPkg, record.packageId) ?: it }
        }
    }

    dropOriginalLinkFilters(manifest)

    val table = apk.tableBlock
    table.getResource(manifest.applicationLabelReference).forEach { it.setValueAsString(record.name) }
    recolorIcon(apk, manifest.iconResourceId, record.hue)

    // Stale v1 data would fail the v2-only signature check.
    apk.zipEntryMap.removeIf(Pattern.compile("^META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC))$"))
    apk.writeApk(out)
}

private const val ORIGINAL_SCHEME = "locationjoystick"
private const val ORIGINAL_LINK_HOST = "locationjoystick.shrtcts.fr"

// The host's asset-links file lists the original's signature, so verified links cannot work in a clone.
internal fun dropOriginalLinkFilters(manifest: AndroidManifestBlock) {
    manifest.applicationElement.listElements("activity").forEach { activity ->
        (activity as ResXmlElement).removeElementsIf { filter ->
            (filter as ResXmlElement).listElements("data").any { data ->
                val element = data as ResXmlElement
                element.searchAttributeByName("scheme")?.valueAsString == ORIGINAL_SCHEME ||
                    element.searchAttributeByName("host")?.valueAsString == ORIGINAL_LINK_HOST
            }
        }
    }
}

private fun rename(
    element: ResXmlElement,
    attribute: String,
    transform: (String) -> String,
) {
    val attr = element.searchAttributeByName(attribute) ?: return
    attr.valueAsString?.let { attr.setValueAsString(transform(it)) }
}

// Resource shrinking renames paths, so follow the references instead of fixed file names.
private fun recolorIcon(
    apk: ApkModule,
    iconId: Int,
    hue: Int,
) {
    val table = apk.tableBlock
    val icon = apk.loadResXmlDocument(table.getResource(iconId).get().valueAsString)
    val background = icon.documentElement.getElement("background")
    val backgroundId = background.searchAttributeByName("drawable").data
    val target = table.getResource(backgroundId).get()
    val path = target.valueAsString
    val document = apk.loadResXmlDocument(path)
    document.documentElement
        .getElement("solid")
        .searchAttributeByName("color")
        .data = backgroundArgb(hue)
    // The loaded document is not written back, so replace the zip entry with its bytes.
    apk.zipEntryMap.add(ByteInputSource(document.bytes, path))
    recolorForeground(apk, icon, hue)
}

private fun recolorForeground(
    apk: ApkModule,
    icon: ResXmlDocument,
    hue: Int,
) {
    val foregroundId =
        icon.documentElement
            .getElement("foreground")
            .searchAttributeByName("drawable")
            .data
    apk.tableBlock.getResource(foregroundId).iterator().forEach { entry ->
        val path = entry.valueAsString
        if (path == null || !path.endsWith(".png")) return@forEach
        val bitmap = BitmapFactory.decodeStream(apk.zipEntryMap.getInputSource(path).openStream())
        val shifted = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(shifted.width * shifted.height)
        shifted.getPixels(pixels, 0, shifted.width, 0, 0, shifted.width, shifted.height)
        shifted.setPixels(IntArray(pixels.size) { shiftHue(pixels[it], hue) }, 0, shifted.width, 0, 0, shifted.width, shifted.height)
        val png = ByteArrayOutputStream()
        shifted.compress(Bitmap.CompressFormat.PNG, 100, png)
        apk.zipEntryMap.add(ByteInputSource(png.toByteArray(), path))
    }
}
