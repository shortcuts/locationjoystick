package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowerCatchUpTest {
    @Test
    fun `within arrival threshold snaps to target and zeroes speed with no bearing change`() {
        val current = LatLng(1.0, 1.0)
        val target = LatLng(1.0, 1.0)

        val result = computeFollowerCatchUp(current, target, activeProfileSpeedMs = 1.4)

        assertEquals(target.latitude, result.latitude, 0.0)
        assertEquals(target.longitude, result.longitude, 0.0)
        assertEquals(0f, result.speedMs)
        assertNull(result.bearing)
    }

    @Test
    fun `step overshooting the target snaps and zeroes speed with no bearing change`() {
        val current = LatLng(1.0, 1.0)
        // ~1m north — well under a single tick's step at typical walking speed.
        val target = LatLng(1.0 + 1.0 / AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE, 1.0)

        val result = computeFollowerCatchUp(current, target, activeProfileSpeedMs = 1.4)

        assertEquals(target.latitude, result.latitude, 1e-9)
        assertEquals(0f, result.speedMs)
        assertNull(result.bearing)
    }

    @Test
    fun `far target advances one step toward it at the active profile speed`() {
        val current = LatLng(0.0, 0.0)
        val target = LatLng(1.0, 0.0)

        val result = computeFollowerCatchUp(current, target, activeProfileSpeedMs = 1.4)

        assertEquals(1.4f, result.speedMs)
        assertTrue("Bearing should be reported while stepping", result.bearing != null)
        assertTrue("Should have moved toward the target, not snapped to it", result.latitude < target.latitude)
        assertTrue("Should have moved off the start position", result.latitude > current.latitude)
    }
}
