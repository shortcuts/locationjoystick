package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot

class CalibrationWalkMathTest {
    private class GoldenLeg(
        val bearing: Double,
        val start: LatLng,
        val target: LatLng,
        val times: DoubleArray,
        val positions: List<LatLng>,
        val moving: List<Boolean>,
        val offsets: List<GroundOffset>,
    )

    // Exported by scripts/export_calibration_walk_golden.py from the bench functions.
    private fun golden(): List<GoldenLeg> {
        val lines =
            javaClass
                .getResourceAsStream("/calibration_walk_golden/legs.txt")!!
                .readBytes()
                .decodeToString()
                .trim()
                .lines()
        val out = mutableListOf<GoldenLeg>()
        var i = 0
        while (i < lines.size) {
            val h = lines[i++].split(" ")
            val n = h[4].toInt()
            val rows = (0 until n).map { lines[i++].split(" ") }
            out +=
                GoldenLeg(
                    h[1].toDouble(),
                    LatLng(h[2].toDouble(), h[3].toDouble()),
                    LatLng(h[5].toDouble(), h[6].toDouble()),
                    DoubleArray(n) { rows[it][0].toDouble() },
                    rows.map { LatLng(it[1].toDouble(), it[2].toDouble()) },
                    rows.map { it[3] == "1" },
                    rows.map { GroundOffset(it[4].toDouble(), it[5].toDouble()) },
                )
        }
        return out
    }

    @Test
    fun matchesBenchGoldens() {
        val legs = golden()
        assertEquals(24, legs.size)
        for (leg in legs) {
            val t = CalibrationWalkMath.commandedTarget(leg.start, leg.bearing, 40.0)
            assertEquals(leg.target.latitude, t.latitude, 1e-9)
            assertEquals(leg.target.longitude, t.longitude, 1e-9)
            assertEquals(leg.moving, CalibrationWalkMath.movingFlags(leg.times, leg.positions))
            for ((i, p) in leg.positions.withIndex()) {
                val o = CalibrationWalkMath.alongTrackOffset(leg.positions[0], p, leg.bearing, leg.positions[0].latitude)
                assertEquals(leg.offsets[i].eastMeters, o.eastMeters, 1e-6)
                assertEquals(leg.offsets[i].northMeters, o.northMeters, 1e-6)
            }
        }
        assertTrue(legs.any { l -> l.moving.any { !it } && l.moving.any { it } })
    }

    @Test
    fun offsetKeepsCrossTrackMotionThatTheProjectionDrops() {
        val p0 = LatLng(0.0, 5.0)
        val p = LatLng(100.0 / CalibrationWalkMath.METERS_PER_DEGREE, 5.0 + 17.6 / CalibrationWalkMath.METERS_PER_DEGREE)
        val o = CalibrationWalkMath.offset(p0, p, 0.0)
        assertEquals(17.6, o.eastMeters, 1e-6)
        assertEquals(100.0, o.northMeters, 1e-6)
        assertEquals(0.0, CalibrationWalkMath.alongTrackOffset(p0, p, 0.0, 0.0).eastMeters, 1e-6)
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

    @Test
    fun movingUsesClampedNeighboursAtTheEnds() {
        // 3 m north per 3 s tick (1 m/s) for frames 0..2, then standing still for 3 frames.
        val p = listOf(0.0, 3.0, 6.0, 6.0, 6.0, 6.0).map { LatLng(it / CalibrationWalkMath.METERS_PER_DEGREE, 0.0) }
        val t = DoubleArray(6) { it * 3.0 }
        // frame 3 spans frames 2..4 (0 m / 6 s); frame 2 spans 1..3 (3 m / 6 s = 0.5 m/s)
        assertEquals(listOf(true, true, true, false, false, false), CalibrationWalkMath.movingFlags(t, p))
    }
}
