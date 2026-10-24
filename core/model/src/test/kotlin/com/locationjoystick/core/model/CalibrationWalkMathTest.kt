package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot

class CalibrationWalkMathTest {
    @Test
    fun offsetIsFlatEarthEastAndNorth() {
        val p0 = LatLng(0.0, 5.0)
        val p = LatLng(100.0 / CalibrationWalkMath.METERS_PER_DEGREE, 5.0 + 17.6 / CalibrationWalkMath.METERS_PER_DEGREE)
        val o = CalibrationWalkMath.offset(p0, p, 0.0)
        assertEquals(17.6, o.eastMeters, 1e-6)
        assertEquals(100.0, o.northMeters, 1e-6)
    }

    @Test
    fun oppositeBearingsReturnToStart() {
        val start = LatLng(52.0, 5.0)
        val there = CalibrationWalkMath.commandedTarget(start, 90.0, 40.0)
        val back = CalibrationWalkMath.commandedTarget(there, 270.0, 40.0)
        val cosLat = cos(Math.toRadians(start.latitude))
        val dEast = (back.longitude - start.longitude) * CalibrationWalkMath.METERS_PER_DEGREE * cosLat
        val dNorth = (back.latitude - start.latitude) * CalibrationWalkMath.METERS_PER_DEGREE
        assertTrue(hypot(dEast, dNorth) < 1e-3)
    }
}
