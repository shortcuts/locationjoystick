package com.locationjoystick.core.map.maplibre

import com.locationjoystick.core.common.util.metersToLatDegrees
import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCameraTest {
    @Test
    fun `walking steps do not snap the camera`() {
        val from = LatLng(48.8566, 2.3522)
        val to = LatLng(48.8570, 2.3522)
        assertFalse(shouldSnapMapCamera(from, to))
    }

    @Test
    fun `teleport-scale jumps snap the camera`() {
        val paris = LatLng(48.8566, 2.3522)
        val london = LatLng(51.5074, -0.1278)
        assertTrue(shouldSnapMapCamera(paris, london))
    }

    @Test
    fun `a jump at the snap threshold snaps`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(metersToLatDegrees(2_001.0), 0.0)
        assertTrue(shouldSnapMapCamera(from, to, snapDistanceMeters = 2_000.0))
    }

    @Test
    fun `just under the snap distance still animates`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(metersToLatDegrees(1_999.0), 0.0)
        assertFalse(shouldSnapMapCamera(from, to, snapDistanceMeters = 2_000.0))
    }
}
