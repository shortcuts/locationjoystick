package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test: callers that need the repository kept in sync (map UI) must not pair
 * [MockLocationService.writeCurrentPosition]'s `positionRef` write with a separate, manually
 * duplicated [LocationRepository.setPositionInternal] call — the single entry point performs
 * both writes itself via `syncRepository = true`.
 */
class WriteCurrentPositionSyncTest {
    private fun newService(): MockLocationService =
        MockLocationService().apply {
            locationRepository = LocationRepository()
        }

    @Test
    fun `updatePosition syncs both positionRef and repository from one call`() {
        val service = newService()
        service.locationRepository.setMockMode(MockMode.JOYSTICK)

        service.updatePosition(1.0, 2.0)

        assertEquals(LatLng(1.0, 2.0), service.getCurrentPosition())
        assertEquals(LatLng(1.0, 2.0), service.locationRepository.currentPosition.value)
    }
}
