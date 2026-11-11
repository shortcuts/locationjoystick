package com.locationjoystick.app.privacy

import com.android.apksig.ApkVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CloneSignerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, String>): File {
        val f = tmp.newFile()
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (n, c) ->
                z.putNextEntry(ZipEntry(n))
                z.write(c.toByteArray())
                z.closeEntry()
            }
        }
        return f
    }

    @Test
    fun `signed apk verifies with v2 only and no stale v1 data`() {
        val unsigned = zip("AndroidManifest.xml" to "x", "META-INF/MANIFEST.MF" to "stale", "META-INF/OLD.RSA" to "stale")
        val out = tmp.newFile()
        val (key, cert) = createSigningKey("com.xq7.notes")
        signApk(unsigned, out, key, cert)
        val result =
            ApkVerifier
                .Builder(out)
                .setMinCheckedPlatformVersion(28)
                .build()
                .verify()
        assertTrue(result.errors.toString(), result.isVerified)
        assertTrue(result.isVerifiedUsingV2Scheme)
        assertFalse(result.isVerifiedUsingV1Scheme)
        assertEquals(cert, result.signerCertificates.single())
    }

    @Test
    fun `certificate outlives the clone`() {
        val (_, cert) = createSigningKey("com.xq7.notes")
        cert.checkValidity()
        assertTrue(cert.notAfter.time - System.currentTimeMillis() > 25L * 365 * 24 * 3600 * 1000)
        assertEquals("CN=com.xq7.notes", cert.subjectX500Principal.name)
    }
}
