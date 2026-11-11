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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyModeViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val record = CloneRecord("com.xq7.notes", "Notes", 200)
    private val status = MutableSharedFlow<InstallStatus>(extraBufferCapacity = 4)
    private val installer =
        mockk<CloneInstaller>(relaxed = true) {
            every { this@mockk.status } returns this@PrivacyModeViewModelTest.status
            every { canInstall() } returns true
        }
    private val builder = mockk<CloneBuilder>()
    private lateinit var store: CloneStore
    private lateinit var flagsStore: PrivacyFlagsStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        store = CloneStore(tmp.newFolder("clone"))
        flagsStore = PrivacyFlagsStore(File(tmp.root, "flags/flags.properties"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun viewModel() = PrivacyModeViewModel(builder, installer, store, flagsStore)

    private fun built() = CloneBuildResult.Built(File(tmp.root, "clone.apk"), record)

    @Test
    fun `start builds then installs and reports Installed`() {
        coEvery { builder.build("Notes") } returns built()
        val vm = viewModel()
        vm.start("Notes")
        assertEquals(CloneOp.Installing(updated = false), vm.state.value.op)
        assertEquals(record, vm.state.value.record)
        status.tryEmit(InstallStatus.Success)
        assertEquals(CloneOp.Installed(updated = false), vm.state.value.op)
    }

    @Test
    fun `start with a record installs as an update`() {
        store.save(record, createSigningKey(record.packageId))
        coEvery { builder.build("Notes") } returns built()
        val vm = viewModel()
        vm.start("Notes")
        status.tryEmit(InstallStatus.Success)
        assertEquals(CloneOp.Installed(updated = true), vm.state.value.op)
    }

    @Test
    fun `installer failure ends Failed`() {
        coEvery { builder.build("Notes") } returns built()
        val vm = viewModel()
        vm.start("Notes")
        status.tryEmit(InstallStatus.Failure("downgrade"))
        assertEquals(CloneOp.Failed("downgrade"), vm.state.value.op)
    }

    @Test
    fun `failed build keeps the old record`() {
        store.save(record, createSigningKey(record.packageId))
        coEvery { builder.build(any()) } returns CloneBuildResult.Failed("out of memory")
        val vm = viewModel()
        vm.start("Other")
        assertEquals(CloneOp.Failed("out of memory"), vm.state.value.op)
        assertEquals(record, vm.state.value.record)
    }

    @Test
    fun `start without install permission never builds`() {
        every { installer.canInstall() } returns false
        val vm = viewModel()
        vm.start("Notes")
        assertEquals(CloneOp.NeedsInstallPermission, vm.state.value.op)
        coVerify(exactly = 0) { builder.build(any()) }
    }

    @Test
    fun `refresh returns to Idle once permission is granted`() {
        every { installer.canInstall() } returns false
        val vm = viewModel()
        vm.start("Notes")
        vm.refreshInstallPermission()
        assertEquals(CloneOp.NeedsInstallPermission, vm.state.value.op)
        every { installer.canInstall() } returns true
        vm.refreshInstallPermission()
        assertEquals(CloneOp.Idle, vm.state.value.op)
    }

    @Test
    fun `start while Building is rejected`() {
        val gate = CompletableDeferred<CloneBuildResult>()
        coEvery { builder.build(any()) } coAnswers { gate.await() }
        val vm = viewModel()
        vm.start("Notes")
        vm.start("Notes")
        assertEquals(CloneOp.Building, vm.state.value.op)
        coVerify(exactly = 1) { builder.build(any()) }
        gate.complete(built())
    }

    @Test
    fun `start while Installing is rejected`() {
        coEvery { builder.build(any()) } returns built()
        val vm = viewModel()
        vm.start("Notes")
        vm.start("Notes")
        assertEquals(CloneOp.Installing(false), vm.state.value.op)
        coVerify(exactly = 1) { builder.build(any()) }
    }

    @Test
    fun `install result from Idle is ignored`() {
        val vm = viewModel()
        status.tryEmit(InstallStatus.Success)
        assertEquals(CloneOp.Idle, vm.state.value.op)
    }

    @Test
    fun `switch on without disclosure asks first and cancel leaves it off`() {
        val vm = viewModel()
        vm.setEnabled(true)
        assertEquals(true, vm.state.value.showDisclosure)
        vm.dismissDisclosure()
        assertEquals(PrivacyFlags(false, false), vm.state.value.flags)
        assertEquals(PrivacyFlags(false, false), flagsStore.read())
    }

    @Test
    fun `confirming the disclosure persists both flags`() {
        val vm = viewModel()
        vm.setEnabled(true)
        vm.confirmDisclosure()
        assertEquals(PrivacyFlags(true, true), vm.state.value.flags)
        assertEquals(PrivacyFlags(true, true), flagsStore.read())
    }

    @Test
    fun `second switch on skips the disclosure`() {
        flagsStore.write(PrivacyFlags(enabled = false, disclosureAccepted = true))
        val vm = viewModel()
        vm.setEnabled(true)
        assertEquals(false, vm.state.value.showDisclosure)
        assertEquals(true, vm.state.value.flags.enabled)
    }

    @Test
    fun `remove clears the record and keeps the switch`() {
        flagsStore.write(PrivacyFlags(true, true))
        store.save(record, createSigningKey(record.packageId))
        val vm = viewModel()
        vm.onRemoved()
        assertNull(vm.state.value.record)
        assertNull(store.read())
        assertEquals(PrivacyFlags(true, true), flagsStore.read())
    }
}
