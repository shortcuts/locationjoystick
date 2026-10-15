package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.model.GroundCalibration
import com.locationjoystick.core.model.GroundMap
import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

class TapToWalkCoordinateTest {
    private val center = LatLng(0.0, 0.0)
    private val screenW = 1080
    private val screenH = 1920
    private val scale = 10.0 // 10 m/px

    @Test
    fun tapCenter_returnsCurrentPosition() {
        val result =
            TapToWalkOverlay.computeWalkTarget(
                center,
                screenW / 2f,
                screenH / 2f,
                screenW,
                screenH,
                scale,
            )
        assertEquals(0.0, result.latitude, 1e-6)
        assertEquals(0.0, result.longitude, 1e-6)
    }

    @Test
    fun tapRight_increasesLongitude() {
        val result =
            TapToWalkOverlay.computeWalkTarget(
                center,
                screenW * 0.75f,
                screenH / 2f,
                screenW,
                screenH,
                scale,
            )
        assertTrue(result.longitude > 0.0)
        assertEquals(0.0, result.latitude, 1e-6)
    }

    @Test
    fun tapLeft_decreasesLongitude() {
        val result =
            TapToWalkOverlay.computeWalkTarget(
                center,
                screenW * 0.25f,
                screenH / 2f,
                screenW,
                screenH,
                scale,
            )
        assertTrue(result.longitude < 0.0)
        assertEquals(0.0, result.latitude, 1e-6)
    }

    @Test
    fun tapAboveCenter_increasesLatitude() {
        val result =
            TapToWalkOverlay.computeWalkTarget(
                center,
                screenW / 2f,
                screenH * 0.25f,
                screenW,
                screenH,
                scale,
            )
        assertTrue(result.latitude > 0.0)
        assertEquals(0.0, result.longitude, 1e-6)
    }

    @Test
    fun tapBelowCenter_decreasesLatitude() {
        val result =
            TapToWalkOverlay.computeWalkTarget(
                center,
                screenW / 2f,
                screenH * 0.75f,
                screenW,
                screenH,
                scale,
            )
        assertTrue(result.latitude < 0.0)
        assertEquals(0.0, result.longitude, 1e-6)
    }

    @Test
    fun largerScale_largerDisplacement() {
        val small =
            TapToWalkOverlay.computeWalkTarget(
                center,
                screenW * 0.75f,
                screenH / 2f,
                screenW,
                screenH,
                1.0,
            )
        val large =
            TapToWalkOverlay.computeWalkTarget(
                center,
                screenW * 0.75f,
                screenH / 2f,
                screenW,
                screenH,
                50.0,
            )
        assertTrue(abs(large.longitude) > abs(small.longitude))
    }

    @Test
    fun latitudeClampedTo90() {
        // Tap far above center with huge scale to force lat > 90
        val result =
            TapToWalkOverlay.computeWalkTarget(
                LatLng(89.0, 0.0),
                screenW / 2f,
                0f,
                screenW,
                screenH,
                50.0,
            )
        assertTrue(result.latitude <= 90.0)
    }

    @Test
    fun displacementSymmetry() {
        // Right tap and left tap from equator should have equal-magnitude longitude shifts
        val right =
            TapToWalkOverlay.computeWalkTarget(
                center,
                screenW * 0.75f,
                screenH / 2f,
                screenW,
                screenH,
                scale,
            )
        val left =
            TapToWalkOverlay.computeWalkTarget(
                center,
                screenW * 0.25f,
                screenH / 2f,
                screenW,
                screenH,
                scale,
            )
        assertEquals(right.longitude, -left.longitude, 1e-9)
    }

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
}
