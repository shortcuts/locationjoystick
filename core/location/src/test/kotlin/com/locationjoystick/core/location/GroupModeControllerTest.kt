package com.locationjoystick.core.location

import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.GroupState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
        val controller = newController(GroupState(role = GroupRole.NONE))

        controller.rediscoverFollowerGroup("AAA111")
        controller.stopFollowerRestoration()
        gate.complete("10.0.0.2" to 8080)
        settle()

        assert(reentered.isEmpty()) { "re-entered follower mode: $reentered" }
    }

    @Test
    fun `joining B during rediscovery of A keeps B`() {
        val controller = newController(GroupState(role = GroupRole.FOLLOWER, groupId = "BBB222"))

        controller.rediscoverFollowerGroup("AAA111")
        gate.complete(null)
        settle()

        coVerify(exactly = 0) { groupRepository.leaveGroup() }
        verify(exactly = 0) { groupRepository.emitGroupLost() }
    }

    @Test
    fun `exhausted rediscovery for the current group leaves it and reports loss`() {
        val controller = newController(GroupState(role = GroupRole.FOLLOWER, groupId = "AAA111"))
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
        val controller = newController(GroupState(role = GroupRole.LEADER, groupId = "AAA111"))

        controller.exitLeaderMode()

        verify { leaderServer.stop() }
        assertEquals(1, leaderExits)
    }
}
