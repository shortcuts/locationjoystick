package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.model.GroundCalibration
import com.locationjoystick.core.model.GroundFlowFit
import com.locationjoystick.core.model.GroundMap
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.StoredGroundCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot

class TapToWalkCoordinateTest {
    private val feet = LatLng(40.0, -74.0)

    private fun groundMap(headingDeg: Double) = GroundMap(GroundCalibration(headingDeg, 7.5, 3.1, -0.0022, 540.0, 1510.0), 1080.0, 2340.0)

    private fun eastNorthMeters(t: LatLng): Pair<Double, Double> =
        Pair(
            (t.longitude - feet.longitude) * 111320.0 * cos(Math.toRadians(feet.latitude)),
            (t.latitude - feet.latitude) * 111320.0,
        )

    private fun groundTap(
        map: GroundMap,
        east: Double,
        north: Double,
    ): LatLng {
        val (px, py) = map.groundToPixel(east, north)
        return TapToWalkOverlay.computeGroundWalkTarget(feet, map, px, py)!!
    }

    @Test
    fun groundMap_tapAtFeet_returnsCurrentPosition() {
        val t = TapToWalkOverlay.computeGroundWalkTarget(feet, groundMap(0.0), 540.0, 1510.0)!!
        assertEquals(feet.latitude, t.latitude, 1e-7)
        assertEquals(feet.longitude, t.longitude, 1e-7)
    }

    @Test
    fun groundMap_30mNorthAnd30mEast() {
        val m = groundMap(0.0)
        val (e1, n1) = eastNorthMeters(groundTap(m, 0.0, 30.0))
        assertEquals(0.0, e1, 0.5)
        assertEquals(30.0, n1, 0.5)
        val (e2, n2) = eastNorthMeters(groundTap(m, 30.0, 0.0))
        assertEquals(30.0, e2, 0.5)
        assertEquals(0.0, n2, 0.5)
    }

    @Test
    fun groundMap_rotatedHeading_stillGeographic() {
        val (e, n) = eastNorthMeters(groundTap(groundMap(37.0), 10.0, 20.0))
        assertEquals(10.0, e, 0.5)
        assertEquals(20.0, n, 0.5)
        assertEquals(hypot(10.0, 20.0), hypot(e, n), 0.5)
    }

    @Test
    fun groundMap_pastHorizon_returnsNull() {
        assertNull(TapToWalkOverlay.computeGroundWalkTarget(feet, groundMap(0.0), 540.0, -3000.0))
    }

    @Test
    fun groundMapFor_usesTheStoredMapOnlyOnItsOwnDisplay() {
        val cal = GroundCalibration(37.0, 7.5, 3.0, -0.0016, 540.0, 1501.0)
        val stored = StoredGroundCalibration(cal, 1080, 2340, 1L, 10, 0.5)
        val own = TapToWalkOverlay.groundMapFor(stored, 1080, 2340).groundToPixel(10.0, 20.0)
        val expected = GroundMap(cal, 1080.0, 2340.0).groundToPixel(10.0, 20.0)
        assertEquals(expected.first, own.first, 1e-9)
        assertEquals(expected.second, own.second, 1e-9)
        val other = TapToWalkOverlay.groundMapFor(stored, 1080, 2400).groundToPixel(10.0, 20.0)
        val provisional = GroundMap(GroundFlowFit.provisional(), 1080.0, 2400.0).groundToPixel(10.0, 20.0)
        assertEquals(provisional.first, other.first, 1e-9)
        assertEquals(provisional.second, other.second, 1e-9)
    }

    @Test
    fun groundMap_southWestHighLatitude_eastScalesByCosLat() {
        val south = LatLng(-69.6, -18.9)
        val m = groundMap(0.0)
        val (px, py) = m.groundToPixel(30.0, -20.0)
        val t = TapToWalkOverlay.computeGroundWalkTarget(south, m, px, py)!!
        val east = (t.longitude - south.longitude) * 111320.0 * cos(Math.toRadians(south.latitude))
        val north = (t.latitude - south.latitude) * 111320.0
        assertEquals(30.0, east, 0.5)
        assertEquals(-20.0, north, 0.5)
    }
}
