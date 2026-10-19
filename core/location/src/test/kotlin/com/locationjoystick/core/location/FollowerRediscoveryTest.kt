package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.GroupState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test

/** A rediscovery in progress must not outlive the group it was started for (group-sync.md, Edge cases). */
class FollowerRediscoveryTest {
    private val gate = CompletableDeferred<Pair<String, Int>?>()
    private val reentered = mutableListOf<String>()

    private fun newService(state: GroupState): MockLocationService =
        MockLocationService().apply {
            locationRepository = LocationRepository()
            followerSyncClient = mockk(relaxed = true)
            groupRepository = mockk(relaxed = true)
            groupNsdManager = mockk()
            every { groupRepository.groupState } returns flowOf(state)
            // Real NSD discovery may ignore cancellation, so the gate does too.
            coEvery { groupNsdManager.discoverByCode("AAA111") } coAnswers {
                withContext(NonCancellable) { gate.await() }
            }
        }

    private fun MockLocationService.rediscoverAndSettle(afterStart: () -> Unit) {
        rediscoverFollowerGroup("AAA111") { host, _ -> reentered += host }
        afterStart()
        runBlocking { followerRediscoveryJob?.join() }
    }

    @Test
    fun `rediscovery after exitFollowerMode does not re-enter follower mode`() {
        val service = newService(GroupState(role = GroupRole.NONE))

        service.rediscoverFollowerGroup("AAA111") { host, _ -> reentered += host }
        val started = service.followerRediscoveryJob
        service.exitFollowerMode()
        gate.complete("10.0.0.2" to 8080)
        runBlocking { started?.join() }

        assert(reentered.isEmpty()) { "re-entered follower mode: $reentered" }
    }

    @Test
    fun `joining B during rediscovery of A keeps B`() {
        val service = newService(GroupState(role = GroupRole.FOLLOWER, groupId = "BBB222"))

        service.rediscoverAndSettle { gate.complete(null) }

        coVerify(exactly = 0) { service.groupRepository.leaveGroup() }
        verify(exactly = 0) { service.groupRepository.emitGroupLost() }
    }

    @Test
    fun `exhausted rediscovery for the current group leaves it and reports loss`() {
        val service = newService(GroupState(role = GroupRole.FOLLOWER, groupId = "AAA111"))

        service.rediscoverAndSettle { gate.complete(null) }

        coVerify(exactly = 1) { service.groupRepository.leaveGroup() }
        verify(exactly = 1) { service.groupRepository.emitGroupLost() }
    }
}
