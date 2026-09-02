package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.MockLocationState
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for issue #61: picking a location on the map before pressing Start must not
 * start spoofing. [MockLocationService.updatePosition] (the sole handler for a teleport, see
 * [com.locationjoystick.core.data.TeleportUseCase]) only writes the pending position — it must
 * never flip [LocationRepository.mockLocationState] to RUNNING, since only that transition
 * registers the test provider and starts pushing fixes to other apps.
 */
class UpdatePositionWhileIdleTest {
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
    fun `updatePosition while idle does not start spoofing`() {
        val service = newService()

        service.updatePosition(1.0, 2.0)

        assertEquals(MockLocationState.IDLE, service.locationRepository.mockLocationState.value)
    }
}
