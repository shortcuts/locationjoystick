package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.LatLng
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

/** Leader Stop must keep the sync server up and tell followers it went inactive (group-sync.md, Edge Cases). */
class LeaderStopKeepsServerTest {
    private val server = LeaderSyncServer()

    @After
    fun tearDown() = server.stop()

    private fun newService(role: GroupRole): MockLocationService =
        MockLocationService().apply {
            locationRepository = LocationRepository()
            leaderSyncServer = server
            groupRepository = mockk(relaxed = true)
            isGroupLeader = role == GroupRole.LEADER
        }

    @Test
    fun `leader stop keeps the service and pushes an inactive update`() {
        val port = server.start("ABC234")
        val service = newService(GroupRole.LEADER)

        assertFalse(service.shouldStopServiceOnStop())
        service.pushInactiveToFollowersIfLeader()

        assertTrue(server.isRunning)
        val body = URL("http://127.0.0.1:$port/position?token=ABC234").readText()
        assertTrue(body, body.contains("\"active\":false"))
    }

    @Test
    fun `non-leader stop still stops the service`() {
        assertTrue(newService(GroupRole.NONE).shouldStopServiceOnStop())
        assertTrue(newService(GroupRole.FOLLOWER).shouldStopServiceOnStop())
    }

    @Test
    fun `teleportToLeaderNow with no target reports unavailable instead of silently doing nothing`() {
        val service = newService(GroupRole.FOLLOWER)
        service.followerCatchUp.setTarget(LatLng(1.0, 2.0), 0f)
        service.followerCatchUp.clearTarget()

        service.teleportToLeaderNow()

        verify { service.groupRepository.emitTeleportUnavailable() }
    }
}
