package com.locationjoystick.app.privacy

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlElement
import org.junit.Assert.assertEquals
import org.junit.Test

class CloneRewriterTest {
    private fun manifestWithFilters(vararg filters: Pair<String, String>): AndroidManifestBlock {
        val manifest = AndroidManifestBlock.empty()
        val application = manifest.getOrCreateApplicationElement()
        val activity = application.newElement("activity")
        filters.forEach { (attribute, value) ->
            val filter = activity.newElement("intent-filter")
            val data = filter.newElement("data")
            data.newAttribute().apply {
                name = attribute
                setValueAsString(value)
            }
        }
        return manifest
    }

    private fun remainingValues(manifest: AndroidManifestBlock): List<String?> =
        (manifest.applicationElement.getElement("activity") as ResXmlElement)
            .listElements("intent-filter")
            .map { ((it as ResXmlElement).getElement("data") as ResXmlElement).getAttributeAt(0).valueAsString }

    @Test
    fun `drops the custom scheme and the verified host filters only`() {
        val manifest =
            manifestWithFilters(
                "scheme" to "locationjoystick",
                "host" to "locationjoystick.shrtcts.fr",
                "scheme" to "geo",
                "host" to "example.com",
            )
        dropOriginalLinkFilters(manifest)
        assertEquals(listOf<String?>("geo", "example.com"), remainingValues(manifest))
    }

    @Test
    fun `hue shift rotates orange to blue and keeps alpha and grey`() {
        assertEquals(0xFF007FFF.toInt(), shiftHue(0xFFFF8000.toInt(), 180))
        assertEquals(0x80404040.toInt(), shiftHue(0x80404040.toInt(), 90))
        assertEquals(0xFFFF8000.toInt(), shiftHue(0xFFFF8000.toInt(), 360))
    }
}
