package com.locationjoystick.app.privacy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CloneInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var installer: CloneInstaller
    private lateinit var apk: File

    @Before
    fun setUp() {
        val context = mockk<Context> { every { cacheDir } returns tmp.root }
        installer = CloneInstaller(context, UnconfinedTestDispatcher())
        apk = File(tmp.root, "privacy-clone/clone.apk").apply { parentFile!!.mkdirs() }.also { it.writeText("apk") }
    }

    private fun result(
        code: Int,
        message: String? = null,
        confirm: Intent? = null,
    ) = mockk<Intent> {
        every { getIntExtra(PackageInstaller.EXTRA_STATUS, any()) } returns code
        every { getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) } returns message
        every { getParcelableExtra<Intent>(Intent.EXTRA_INTENT) } returns confirm
        every { getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) } returns confirm
    }

    private suspend fun statusOf(intent: Intent): InstallStatus {
        var status: InstallStatus? = null
        installer.status.test {
            installer.onResult(intent)
            status = awaitItem()
        }
        return status!!
    }

    @Test
    fun `success emits Success and deletes the apk`() =
        runTest {
            assertEquals(InstallStatus.Success, statusOf(result(PackageInstaller.STATUS_SUCCESS)))
            assertFalse(apk.exists())
        }

    @Test
    fun `pending user action emits the confirm intent and keeps the apk`() =
        runTest {
            val confirm = mockk<Intent>()
            val intent = result(PackageInstaller.STATUS_PENDING_USER_ACTION, confirm = confirm)
            assertEquals(InstallStatus.NeedsUserAction(confirm), statusOf(intent))
            assertTrue(apk.exists())
        }

    @Test
    fun `pending user action without a confirm intent fails and deletes the apk`() =
        runTest {
            assertEquals(InstallStatus.Failure("no confirm screen"), statusOf(result(PackageInstaller.STATUS_PENDING_USER_ACTION)))
            assertFalse(apk.exists())
        }

    @Test
    fun `failure emits its message and deletes the apk`() =
        runTest {
            assertEquals(InstallStatus.Failure("downgrade"), statusOf(result(PackageInstaller.STATUS_FAILURE, "downgrade")))
            assertFalse(apk.exists())
        }

    @Test
    fun `failure without a message names the status code`() =
        runTest {
            assertEquals(InstallStatus.Failure("status 4"), statusOf(result(PackageInstaller.STATUS_FAILURE_INVALID)))
        }
}
