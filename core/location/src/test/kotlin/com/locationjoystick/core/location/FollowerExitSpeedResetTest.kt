package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.MockMode
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for a bug where leaving a Group Sync follower session left the last
 * leader-supplied speed/bearing stuck in [MockLocationService], so switching speed profiles
 * afterward updated the UI but never changed the actually reported GPS speed.
 */
class FollowerExitSpeedResetTest {
    private fun newService(): MockLocationService =
        MockLocationService().apply {
            locationRepository = LocationRepository()
            followerSyncClient = mockk(relaxed = true)
        }

    @Test
    fun `exitFollowerMode zeroes stale speed and bearing left over from the leader`() {
        val service = newService()

        // Simulate the leader's last-reported speed (e.g. Run profile) still being live, then
        // entering FOLLOWER mode as the real follower tick path does (mode set before ticks arrive).
        service.updatePositionWithVector(lat = 1.0, lon = 2.0, speedMs = 3.5f, bearing = 90f)
        service.locationRepository.setMockMode(MockMode.FOLLOWER)
        assertEquals(3.5f, service.captureSnapshot(nowMs = 0L).speedMs)

        service.exitFollowerMode()

        val snapshot = service.captureSnapshot(nowMs = 0L)
        assertEquals(0.0f, snapshot.speedMs)
        assertEquals(0.0f, snapshot.bearing)
        assertEquals(MockMode.TELEPORT, service.locationRepository.currentMode.value)
    }

    @Test
    fun `exitFollowerMode stops polling and does not resume`() {
        val service = newService()

        service.exitFollowerMode()

        verify { service.followerSyncClient.stopPolling() }
    }
}
