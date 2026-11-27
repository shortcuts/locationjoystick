package com.locationjoystick.app.privacy

import android.content.Context
import android.content.pm.PackageManager
import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkFormatException
import com.locationjoystick.app.BuildConfig
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.di.IoDispatcher
import com.locationjoystick.core.common.util.isNewerVersion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

data class ReleaseApk(
    val version: String,
    val url: String,
)

sealed interface UpdatePrepareResult {
    data object UpToDate : UpdatePrepareResult

    data class Ready(
        val apk: File,
    ) : UpdatePrepareResult

    data class Failed(
        val reason: String,
    ) : UpdatePrepareResult
}

/** Reads GitHub's latest-release JSON. The release workflow attaches one APK. */
fun parseLatestRelease(json: String): ReleaseApk? =
    runCatching {
        val release = JSONObject(json)
        val assets = release.getJSONArray("assets")
        val url =
            (0 until assets.length())
                .map { assets.getJSONObject(it).getString("browser_download_url") }
                .firstOrNull { it.startsWith("https://") && it.endsWith(".apk") } ?: return null
        ReleaseApk(release.getString("tag_name").removePrefix("v"), url)
    }.getOrNull()

/** True only when every signer of [apk] is in [expected], so a tampered or foreign APK never installs. */
fun isSignedBy(
    apk: File,
    expected: Set<String>,
): Boolean {
    if (expected.isEmpty()) return false
    val result =
        try {
            ApkVerifier
                .Builder(apk)
                .setMinCheckedPlatformVersion(28)
                .build()
                .verify()
        } catch (e: ApkFormatException) {
            return false
        } catch (e: IOException) {
            return false
        }
    return result.isVerified && result.signerCertificates.map { certDigest(it.encoded) }.toSet() == expected
}

fun installedSignerDigests(context: Context): Set<String> =
    context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        .signingInfo
        ?.apkContentsSigners
        ?.map { certDigest(it.toByteArray()) }
        ?.toSet()
        .orEmpty()

/** Downloads the latest GitHub release and, inside a copy, rewrites it into that copy. See docs/features/privacy-mode.md. */
@Singleton
class AppUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: CloneStore,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        // A copy keeps the original application ID in BuildConfig.
        private val isCopy get() = context.packageName != BuildConfig.APPLICATION_ID

        suspend fun prepare(): UpdatePrepareResult =
            withContext(dispatcher) {
                try {
                    prepareBlocking()
                } catch (e: IOException) {
                    UpdatePrepareResult.Failed(e.message ?: "network error")
                } catch (e: GeneralSecurityException) {
                    UpdatePrepareResult.Failed(e.message ?: "signing error")
                } catch (e: ApkFormatException) {
                    UpdatePrepareResult.Failed(e.message ?: "apk format error")
                } catch (e: RuntimeException) {
                    UpdatePrepareResult.Failed(e.message ?: "update error")
                } catch (e: OutOfMemoryError) {
                    UpdatePrepareResult.Failed(e.message ?: "out of memory")
                }
            }

        private fun prepareBlocking(): UpdatePrepareResult {
            val body = open(AppConstants.UpdateCheckConstants.GITHUB_API_URL).inputStream.use { it.readBytes() }
            val release = parseLatestRelease(body.decodeToString()) ?: return UpdatePrepareResult.Failed("no APK in the latest release")
            if (!isNewerVersion(release.version, AppConstants.AppInfo.VERSION_NAME)) return UpdatePrepareResult.UpToDate
            if (isCopy && store.origin().isEmpty()) importEmbeddedPayload()
            val download = File(File(context.cacheDir, "privacy-clone").apply { mkdirs() }, "download.apk")
            try {
                open(release.url).inputStream.use { input -> download.outputStream().use { input.copyTo(it) } }
                return installable(download, isCopy)
            } finally {
                download.delete()
            }
        }

        /** Checks the signers of [download] and, in a copy, rewrites it into that copy. */
        internal fun installable(
            download: File,
            isCopy: Boolean,
        ): UpdatePrepareResult {
            val expected = if (isCopy) store.origin() else installedSignerDigests(context)
            if (!isSignedBy(download, expected)) return UpdatePrepareResult.Failed("the download is not signed by this app's publisher")
            // CloneInstaller deletes clone.apk at each terminal install status.
            val dir = File(context.cacheDir, "privacy-clone").apply { mkdirs() }
            val apk = File(dir, "clone.apk")
            if (!isCopy) {
                check(download.renameTo(apk)) { "cannot replace clone.apk" }
                return UpdatePrepareResult.Ready(apk)
            }
            val record = store.read() ?: return UpdatePrepareResult.Failed("this copy has no update key")
            val signer = store.loadSigner() ?: return UpdatePrepareResult.Failed("this copy has no update key")
            val unsigned = File(dir, "clone-unsigned.apk")
            try {
                rewriteApk(download, BuildConfig.APPLICATION_ID, record, unsigned)
                signApk(unsigned, apk, signer.first, signer.second)
            } finally {
                unsigned.delete()
            }
            return UpdatePrepareResult.Ready(apk)
        }

        // The copy's own rewrite omits the payload, so the key leaves the APK at the first self-update.
        private fun importEmbeddedPayload() {
            val bytes =
                try {
                    context.assets.open(PAYLOAD_ASSET).use { it.readBytes() }
                } catch (e: IOException) {
                    return
                }
            store.importPayload(bytes)
        }

        private fun open(url: String): HttpURLConnection {
            val connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = AppConstants.UpdateCheckConstants.CONNECT_TIMEOUT_MS
                    readTimeout = AppConstants.UpdateCheckConstants.READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", AppConstants.UpdateCheckConstants.userAgent())
                }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            return connection
        }
    }
