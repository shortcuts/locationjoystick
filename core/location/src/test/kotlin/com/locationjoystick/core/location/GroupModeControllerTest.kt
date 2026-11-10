package com.locationjoystick.core.location

import com.locationjoystick.core.common.util.NetworkUtils
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.model.GroupState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/** A rediscovery in progress must not outlive the group it was started for (group-sync.md, Edge cases). */
class GroupModeControllerTest {
    private val gate = CompletableDeferred<Pair<String, Int>?>()
    private val reentered = mutableListOf<String>()
    private var leaderExits = 0
    private val job = SupervisorJob()
    private val groupRepository = mockk<GroupRepository>(relaxed = true)
    private val nsd = mockk<NsdCodeManager>(relaxed = true)
    private val leaderServer = mockk<LeaderSyncServer>(relaxed = true)
    private lateinit var controller: GroupModeController

    private fun newController(state: GroupState): GroupModeController {
        every { groupRepository.groupState } returns flowOf(state)
        // Real NSD discovery may ignore cancellation, so the gate does too.
        coEvery { nsd.discoverByCode("AAA111") } coAnswers { withContext(NonCancellable) { gate.await() } }
        return GroupModeController(
            scope = CoroutineScope(job),
            leaderSyncServer = leaderServer,
            controlApiRoutes = mockk(relaxed = true),
            contentApiRoutes = mockk(relaxed = true),
            groupNsdManager = nsd,
            groupRepository = groupRepository,
            enterFollowerMode = { host, _, _ -> reentered += host },
            exitFollowerMode = { controller.stopFollowerRestoration() },
            onLeaderExited = { leaderExits++ },
        ).also { controller = it }
    }

    private fun settle() = runBlocking { job.children.toList().forEach { it.join() } }

    @Test
    fun `rediscovery after leaving the follower mode does not re-enter it`() {
        val controller = newController(GroupState.None)

        controller.rediscoverFollowerGroup("AAA111")
        controller.stopFollowerRestoration()
        gate.complete("10.0.0.2" to 8080)
        settle()

        assert(reentered.isEmpty()) { "re-entered follower mode: $reentered" }
    }

    @Test
    fun `joining B during rediscovery of A keeps B`() {
        val controller = newController(GroupState.Follower(groupId = "BBB222", host = "10.0.0.9", port = 8080))

        controller.rediscoverFollowerGroup("AAA111")
        gate.complete(null)
        settle()

        coVerify(exactly = 0) { groupRepository.leaveGroup() }
        verify(exactly = 0) { groupRepository.emitGroupLost() }
    }

    @Test
    fun `exhausted rediscovery for the current group leaves it and reports loss`() {
        val controller = newController(GroupState.Follower(groupId = "AAA111", host = "10.0.0.9", port = 8080))
        // A real leaveGroup suspends, so a cancelled caller aborts it before emitGroupLost.
        coEvery { groupRepository.leaveGroup() } coAnswers { yield() }

        controller.rediscoverFollowerGroup("AAA111")
        gate.complete(null)
        settle()

        coVerify(exactly = 1) { groupRepository.leaveGroup() }
        verify(exactly = 1) { groupRepository.emitGroupLost() }
    }

    @Test
    fun `exit leader stops the server and reports it`() {
        val controller = newController(GroupState.Leader(groupId = "AAA111", host = "10.0.0.9", port = 8080))

        controller.exitLeaderMode()

        verify { leaderServer.stop() }
        assertEquals(1, leaderExits)
    }

    @Test
    fun `restoration then rediscovery replaces and cancels the first job`() {
        val controller = newController(GroupState.None)

        controller.startFollowerRestoration("AAA111")
        val first = (controller.state as GroupModeState.RestoringFollower).job
        controller.rediscoverFollowerGroup("AAA111")

        assert(controller.state is GroupModeState.Rediscovering)
        assert(first.isCancelled)
        controller.stopFollowerRestoration()
        assertEquals(GroupModeState.Idle, controller.state)
        gate.complete(null)
        settle()
    }

    @Test
    fun `entering leader mode advertises and stopAdvertising returns to Idle`() {
        val controller = newController(GroupState.None)
        every { leaderServer.isRunning } returns false
        every { leaderServer.start("L1") } returns 1234
        mockkObject(NetworkUtils)
        every { NetworkUtils.getLocalIpAddress() } returns "10.0.0.1"

        controller.enterLeaderMode("L1")
        settle()
        assertEquals(GroupModeState.Advertising("L1"), controller.state)

        controller.stopAdvertising()
        assertEquals(GroupModeState.Idle, controller.state)
        verify { nsd.stopAdvertising("L1") }
        unmockkObject(NetworkUtils)
    }

    @Test
    fun `follower jobs are rejected while advertising and stop leaves advertising alone`() {
        val controller = newController(GroupState.None)
        every { leaderServer.isRunning } returns false
        every { leaderServer.start("L1") } returns 1234
        mockkObject(NetworkUtils)
        every { NetworkUtils.getLocalIpAddress() } returns "10.0.0.1"
        controller.enterLeaderMode("L1")
        settle()

        controller.startFollowerRestoration("AAA111")
        controller.rediscoverFollowerGroup("AAA111")
        controller.stopFollowerRestoration()

        assertEquals(GroupModeState.Advertising("L1"), controller.state)
        unmockkObject(NetworkUtils)
    }

    @Test
    fun `stopAdvertising while idle does nothing`() {
        val controller = newController(GroupState.None)
        controller.stopAdvertising()
        assertEquals(GroupModeState.Idle, controller.state)
        verify(exactly = 0) { nsd.stopAdvertising(any()) }
    }
}
