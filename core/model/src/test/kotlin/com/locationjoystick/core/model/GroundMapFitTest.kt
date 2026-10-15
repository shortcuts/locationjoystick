package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GroundMapFitTest {
    private fun resource(name: String) =
        javaClass
            .getResourceAsStream("/flow_golden/$name")!!
            .readBytes()
            .decodeToString()
            .trim()
            .lines()

    private fun moves(dataset: String): List<LegMove> =
        resource("moves.txt")
            .map { it.split(" ") }
            .filter { it[0] == dataset }
            .map { p ->
                val v = p.drop(1).map { it.toDouble() }
                LegMove(GroundOffset(v[0], v[1]), listOf(BandFlow(v[2], v[3], v[4], v[5], v[6])))
            }

    @Test
    fun matchesBenchFitOnBothDatasets() {
        for (line in resource("fit.txt")) {
            val p = line.split(" ")
            val e = p.drop(1).map { it.toDouble() }
            val fit = GroundMapFit.fit(moves(p[0]), 540.0, 1510.0, 1080, 2340)!!
            val c = fit.calibration
            // bench returns the mirrored equivalent (s < 0, theta + 180); the port reports the positive-scale one
            val flip = e[1] < 0
            val sg = if (flip) -1.0 else 1.0
            val theta = e[0] - if (flip) 180.0 else 0.0
            assertEquals("${p[0]} theta", theta, c.headingDeg, 1.5)
            assertEquals("${p[0]} s", sg * e[1], c.lateralPxPerM, 0.03 * sg * e[1])
            assertEquals("${p[0]} sy", sg * e[2], c.forwardPxPerM, 0.03 * kotlin.math.abs(e[2]))
            assertEquals("${p[0]} a", sg * e[3], c.perspective, 0.05 * kotlin.math.abs(e[3]))
            assertEquals(540.0, c.feetX, 1e-9)
            assertEquals(1510.0, c.feetY, 1e-9)
            assertEquals(e[4], fit.medianAbsPx, 0.3)
            assertEquals("bench n counts flows / 2", e[5].toInt() * 2, fit.flowCount)
        }
    }

    @Test
    fun fitRoundTripsThroughGroundMap() {
        val c = GroundMapFit.fit(moves("A"), 540.0, 1510.0, 1080, 2340)!!.calibration
        val map = GroundMap(c, 1080.0, 2340.0)
        val o = map.pixelToGround(540.0, 1000.0)!!
        val (px, py) = map.groundToPixel(o.eastMeters, o.northMeters)
        assertEquals(540.0, px, 1e-6)
        assertEquals(1000.0, py, 1e-6)
    }

    @Test
    fun feetPixelIsStoredInReferencePixels() {
        val c = GroundMapFit.fit(moves("A"), 270.0, 755.0, 540, 1170)
        // flows are device px of a 540x1170 screen here: scaled to reference, the fit still exists
        assertNotNull(c)
        assertEquals(540.0, c!!.calibration.feetX, 1e-9)
        assertEquals(1510.0, c.calibration.feetY, 1e-9)
    }

    @Test
    fun fewerThanFortyUsableFlowsReturnsNull() {
        assertNull(GroundMapFit.fit(moves("A").take(39), 540.0, 1510.0, 1080, 2340))
        val weak = moves("A").map { m -> LegMove(m.d, m.bands.map { it.copy(response = 0.1) }) }
        assertNull(GroundMapFit.fit(weak, 540.0, 1510.0, 1080, 2340))
    }
}
