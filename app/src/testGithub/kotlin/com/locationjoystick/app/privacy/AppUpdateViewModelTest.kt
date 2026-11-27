package com.locationjoystick.app.privacy

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    private val status = MutableSharedFlow<InstallStatus>(extraBufferCapacity = 4)
    private var canInstall = true
    private val installer =
        mockk<CloneInstaller>(relaxed = true) {
            every { this@mockk.status } returns this@AppUpdateViewModelTest.status
            every { canInstall() } answers { canInstall }
        }
    private val updater = mockk<AppUpdater>()
    private val apk = File("clone.apk")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun viewModel() = AppUpdateViewModel(updater, installer)

    @Test
    fun `ready update is handed to the installer`() {
        coEvery { updater.prepare() } returns UpdatePrepareResult.Ready(apk)
        val vm = viewModel()
        vm.update()
        assertEquals(UpdateOp.Installing, vm.op.value)
        coVerify { installer.install(apk) }
    }

    @Test
    fun `install failure is reported`() {
        coEvery { updater.prepare() } returns UpdatePrepareResult.Ready(apk)
        val vm = viewModel()
        vm.update()
        status.tryEmit(InstallStatus.Failure("refused"))
        assertEquals(UpdateOp.Failed("refused"), vm.op.value)
    }

    @Test
    fun `install success returns to idle`() {
        coEvery { updater.prepare() } returns UpdatePrepareResult.Ready(apk)
        val vm = viewModel()
        vm.update()
        status.tryEmit(InstallStatus.Success)
        assertEquals(UpdateOp.Idle, vm.op.value)
    }

    @Test
    fun `no newer release reports up to date`() {
        coEvery { updater.prepare() } returns UpdatePrepareResult.UpToDate
        val vm = viewModel()
        vm.update()
        assertEquals(UpdateOp.UpToDate, vm.op.value)
    }

    @Test
    fun `prepare failure is reported and nothing installs`() {
        coEvery { updater.prepare() } returns UpdatePrepareResult.Failed("bad signature")
        val vm = viewModel()
        vm.update()
        assertEquals(UpdateOp.Failed("bad signature"), vm.op.value)
        coVerify(exactly = 0) { installer.install(any()) }
    }

    @Test
    fun `missing install permission waits for the grant`() {
        canInstall = false
        val vm = viewModel()
        vm.update()
        assertEquals(UpdateOp.NeedsInstallPermission, vm.op.value)
        vm.refreshInstallPermission()
        assertEquals(UpdateOp.NeedsInstallPermission, vm.op.value)
        canInstall = true
        vm.refreshInstallPermission()
        assertEquals(UpdateOp.Idle, vm.op.value)
    }

    @Test
    fun `update while preparing is ignored`() {
        val gate = CompletableDeferred<UpdatePrepareResult>()
        coEvery { updater.prepare() } coAnswers { gate.await() }
        val vm = viewModel()
        vm.update()
        vm.update()
        assertEquals(UpdateOp.Preparing, vm.op.value)
        coVerify(exactly = 1) { updater.prepare() }
    }

    @Test
    fun `install status without an install in flight is ignored`() {
        val vm = viewModel()
        status.tryEmit(InstallStatus.Failure("stale"))
        assertEquals(UpdateOp.Idle, vm.op.value)
    }
}
