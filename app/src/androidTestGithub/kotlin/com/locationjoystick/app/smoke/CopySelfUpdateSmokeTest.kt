package com.locationjoystick.app.smoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.locationjoystick.app.privacy.AppUpdater
import com.locationjoystick.app.privacy.CloneBuildResult
import com.locationjoystick.app.privacy.CloneBuilder
import com.locationjoystick.app.privacy.CloneStore
import com.locationjoystick.app.privacy.PAYLOAD_ASSET
import com.locationjoystick.app.privacy.UpdatePrepareResult
import com.locationjoystick.app.privacy.certDigest
import com.locationjoystick.app.privacy.installedSignerDigests
import com.locationjoystick.app.privacy.isSignedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * GitHub flavor only. Runs the real clone build and the copy's update rewrite on the installed APK, which
 * stands in for the GitHub release. No network and no install: the system confirm screen cannot be tapped here.
 */
@RunWith(AndroidJUnit4::class)
class CopySelfUpdateSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "copy-self-update-test")

    @After
    fun tearDown() {
        dir.deleteRecursively()
        File(context.cacheDir, "privacy-clone").deleteRecursively()
    }

    private fun installedApkCopy(): File = File(context.applicationInfo.sourceDir).copyTo(File(dir, "release.apk"), overwrite = true)

    @Test
    fun copy_carries_its_key_and_updates_from_a_release_without_the_original() =
        runBlocking {
            val built = CloneBuilder(context, CloneStore(File(dir, "original")), Dispatchers.IO).build("Notes")
            check(built is CloneBuildResult.Built) { "build failed: $built" }
            val payload = ZipFile(built.apk).use { z -> z.getInputStream(z.getEntry("assets/$PAYLOAD_ASSET")).readBytes() }

            // The copy's own store: nothing from the original's files dir.
            val copyStore = CloneStore(File(dir, "copy"))
            assertTrue(copyStore.importPayload(payload))
            assertEquals(built.record, copyStore.read())
            assertEquals(installedSignerDigests(context), copyStore.origin())
            val copyDigest = certDigest(copyStore.loadSigner()!!.second.encoded)
            assertTrue(isSignedBy(built.apk, setOf(copyDigest)))

            val result = AppUpdater(context, copyStore, Dispatchers.IO).installable(installedApkCopy(), isCopy = true)
            check(result is UpdatePrepareResult.Ready) { "update failed: $result" }
            assertTrue(isSignedBy(result.apk, setOf(copyDigest)))
            assertEquals(built.record.packageId, context.packageManager.getPackageArchiveInfo(result.apk.path, 0)?.packageName)
            assertNull(ZipFile(result.apk).use { it.getEntry("assets/$PAYLOAD_ASSET") })
        }

    @Test
    fun copy_refuses_a_release_not_signed_by_the_original() {
        val store = CloneStore(File(dir, "copy"))
        val result = AppUpdater(context, store, Dispatchers.IO).installable(installedApkCopy(), isCopy = true)
        assertTrue("expected refusal, got $result", result is UpdatePrepareResult.Failed)
    }

    @Test
    fun original_accepts_a_release_signed_like_itself() {
        val result = AppUpdater(context, CloneStore(File(dir, "original")), Dispatchers.IO).installable(installedApkCopy(), isCopy = false)
        assertTrue("expected ready, got $result", result is UpdatePrepareResult.Ready)
    }
}
