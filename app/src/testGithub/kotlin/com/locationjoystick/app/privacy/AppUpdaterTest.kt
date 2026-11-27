package com.locationjoystick.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppUpdaterTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun release(vararg urls: String) =
        """{"tag_name":"v1.2.3","assets":[${urls.joinToString(",") { """{"browser_download_url":"$it"}""" }}]}"""

    @Test
    fun `picks the apk asset and strips the tag prefix`() {
        val json =
            release("https://github.com/a/b/releases/download/v1.2.3/notes.txt", "https://github.com/a/b/releases/download/v1.2.3/app.apk")
        assertEquals(ReleaseApk("1.2.3", "https://github.com/a/b/releases/download/v1.2.3/app.apk"), parseLatestRelease(json))
    }

    @Test
    fun `release without an apk or with a plain http apk is rejected`() {
        assertNull(parseLatestRelease(release("https://github.com/a/b/notes.txt")))
        assertNull(parseLatestRelease(release("http://github.com/a/b/app.apk")))
        assertNull(parseLatestRelease("not json"))
    }

    private fun signedApk(packageId: String): Pair<File, String> {
        val unsigned = tmp.newFile()
        ZipOutputStream(unsigned.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("AndroidManifest.xml"))
            z.write("x".toByteArray())
            z.closeEntry()
        }
        val out = tmp.newFile()
        val (key, cert) = createSigningKey(packageId)
        signApk(unsigned, out, key, cert)
        return out to certDigest(cert.encoded)
    }

    @Test
    fun `accepts only an apk signed by the expected signer`() {
        val (apk, digest) = signedApk("com.xq7.notes")
        val (_, other) = signedApk("com.xq7.other")
        assertTrue(isSignedBy(apk, setOf(digest)))
        assertFalse(isSignedBy(apk, setOf(other)))
        assertFalse(isSignedBy(apk, emptySet()))
    }

    @Test
    fun `unsigned file is rejected`() {
        assertFalse(isSignedBy(tmp.newFile().apply { writeText("not an apk") }, setOf("ab")))
    }
}
