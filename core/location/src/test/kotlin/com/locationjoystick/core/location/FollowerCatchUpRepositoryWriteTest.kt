package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the follower map staying stuck at its initial position: the
 * FOLLOWER-mode catch-up step must write the new position into [LocationRepository], not
 * just [MockLocationService.positionRef], since the map UI renders only from the repository.
 */
class FollowerCatchUpRepositoryWriteTest {
    private fun newService(): MockLocationService =
        MockLocationService().apply {
            locationRepository = LocationRepository()
        }

    @Test
    fun `advanceFollowerCatchUp writes the stepped position into LocationRepository`() {
        val service = newService()
        service.locationRepository.setMockMode(MockMode.FOLLOWER)
        service.locationRepository.setPositionInternal(LatLng(0.0, 0.0))
        service.followerCatchUp.setTarget(LatLng(1.0, 0.0), leaderBearing = 0f)

        service.advanceFollowerCatchUp()

        val repoPos = service.locationRepository.currentPosition.value
        assertEquals(service.getCurrentPosition().latitude, repoPos!!.latitude, 0.0)
        assertEquals(service.getCurrentPosition().longitude, repoPos.longitude, 0.0)
        // Confirms it actually moved off the start, not just mirrored a no-op.
        assertEquals(true, repoPos.latitude > 0.0)
    }

    @Test
    fun `advanceFollowerCatchUp is a no-op outside FOLLOWER mode`() {
        val service = newService()
        service.locationRepository.setMockMode(MockMode.JOYSTICK)
        service.locationRepository.setPositionInternal(LatLng(0.0, 0.0))
        service.followerCatchUp.setTarget(LatLng(1.0, 0.0), leaderBearing = 0f)

        service.advanceFollowerCatchUp()

        assertEquals(
            0.0,
            service.locationRepository.currentPosition.value!!
                .latitude,
            0.0,
        )
    }
}
