package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for GitHub issue #64: after "Walk here" / "Walk here via roads" arrives at
 * its target, WalkCoordinator's zero-speed tick reaches [MockLocationService] through an Intent
 * (updatePositionWithVector), which can be delivered after mode has already flipped away from
 * WALK_TO and gets silently rejected by the mode gate — leaving speed stuck at its last nonzero
 * value. [MockLocationService.onWalkTargetChanged] reacts to
 * [LocationRepository.walkTarget] clearing directly, in-process, so the reset no longer depends
 * on that race.
 */
class WalkTargetClearedSpeedResetTest {
    private fun newService(): MockLocationService =
        MockLocationService().apply {
            locationRepository = LocationRepository()
            altitudeAnchor =
                AltitudeAnchorCoordinator(
                    elevationRepository = mockk(relaxed = true),
                    settingsRepository = mockk(relaxed = true),
                    locationRepository = locationRepository,
                )
        }

    @Test
    fun `walk target clearing zeroes stale speed even if the arrival intent never lands`() {
        val service = newService()
        service.locationRepository.setMockMode(MockMode.WALK_TO)
        service.updatePositionWithVector(1.0, 2.0, speedMs = 3.5f, bearing = 90f)
        // Simulate the mode flip to TELEPORT racing ahead of the arrival Intent — the intent
        // never arrives here, so updatePositionWithVector's own zero-tick is never applied.
        service.locationRepository.setMockMode(MockMode.TELEPORT)

        // Mirrors what the observeLocationState() collector does when walkTarget clears —
        // exercised directly since that coroutine isn't running in this bare unit test.
        service.onWalkTargetChanged(LatLng(3.0, 4.0))
        service.onWalkTargetChanged(null)

        val snapshot = service.captureSnapshot(nowMs = 0L)
        assertEquals(0.0f, snapshot.speedMs, 0.0f)
        assertEquals(0.0f, snapshot.bearing, 0.0f)
    }

    @Test
    fun `walk target becoming non-null does not clear an in-progress motion vector`() {
        val service = newService()
        service.locationRepository.setMockMode(MockMode.WALK_TO)
        service.updatePositionWithVector(1.0, 2.0, speedMs = 3.5f, bearing = 90f)

        service.onWalkTargetChanged(LatLng(5.0, 6.0))

        val snapshot = service.captureSnapshot(nowMs = 0L)
        assertEquals(3.5f, snapshot.speedMs, 0.0f)
    }
}
