package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.MockMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for GitHub issue #47: [MockLocationService.updatePositionWithVector] leaves
 * [MockLocationService]'s speed/bearing fields nonzero after a joystick drag ends, so realism
 * ticks keep taking the moving-jitter branch indefinitely unless [MockLocationService.clearMotionVector]
 * is called.
 */
class ClearMotionVectorTest {
    private fun newService(): MockLocationService =
        MockLocationService().apply {
            locationRepository = LocationRepository()
        }

    @Test
    fun `clearMotionVector zeroes speed and bearing left by updatePositionWithVector`() {
        val service = newService()
        service.locationRepository.setMockMode(MockMode.JOYSTICK)
        service.updatePositionWithVector(1.0, 2.0, speedMs = 3.5f, bearing = 90f)

        service.clearMotionVector()

        val snapshot = service.captureSnapshot(nowMs = 0L)
        assertEquals(0.0f, snapshot.speedMs, 0.0f)
        assertEquals(0.0f, snapshot.bearing, 0.0f)
    }
}
