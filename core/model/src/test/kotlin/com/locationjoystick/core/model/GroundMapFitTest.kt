package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** Flows the camera `cal` would show for a player move of `d`, on a grid of bands. */
    private fun syntheticMoves(
        cal: GroundCalibration,
        bearings: List<Double>,
        ys: List<Double> = listOf(600.0, 800.0, 1000.0, 1200.0, 1350.0),
    ): List<LegMove> {
        val map = GroundMap(cal, 1080.0, 2340.0)
        return bearings.flatMap { b ->
            val d = GroundOffset(6.0 * kotlin.math.sin(Math.toRadians(b)), 6.0 * kotlin.math.cos(Math.toRadians(b)))
            List(6) {
                val bands =
                    ys.flatMap { y ->
                        listOf(250.0, 850.0).map { x ->
                            val g = map.pixelToGround(x, y)!!
                            val (qx, qy) = map.groundToPixel(g.eastMeters - d.eastMeters, g.northMeters - d.northMeters)
                            BandFlow(qx - x, qy - y, 0.9, x, y)
                        }
                    }
                LegMove(d, bands)
            }
        }
    }

    @Test
    fun moveResidualsAreSmallForTheFitCameraAndLargeAfterATwist() {
        val stored = GroundMapFit.fit(moves("A"), 540.0, 1510.0, 1080, 2340)!!.calibration
        val same = GroundMapFit.moveResidualsPx(stored, syntheticMoves(stored, listOf(0.0, 90.0)), 1080, 2340)
        assertEquals(0.0, same.filterNotNull().max(), 0.5)
        val twisted = stored.copy(headingDeg = stored.headingDeg + 30.0)
        val seen = GroundMapFit.moveResidualsPx(stored, syntheticMoves(twisted, listOf(0.0, 90.0)), 1080, 2340)
        val min = seen.filterNotNull().min()
        assertEquals("twisted residual $min px", true, min > 5.0)
    }

    @Test
    fun aMoveWithTooFewUsableBandsHasNoResidual() {
        val stored = GroundMapFit.fit(moves("A"), 540.0, 1510.0, 1080, 2340)!!.calibration
        val weak = syntheticMoves(stored, listOf(0.0)).take(1).map { m -> LegMove(m.d, m.bands.map { it.copy(response = 0.1) }) }
        assertNull(GroundMapFit.moveResidualsPx(stored, weak, 1080, 2340)[0])
    }

    @Test
    fun moveResidualsIgnoreBandsAboveHorizon() {
        val forward = 3.0
        val cal = GroundCalibration(0.0, 7.0, forward, forward / 702.0, 540.0, 1510.0) // horizon row 702 = 0.3 of 2340
        assertNotNull(GroundMap(cal, 1080.0, 2340.0).pixelToGround(540.0, 900.0))
        val garbage =
            listOf(100.0, 200.0, 300.0, 500.0, 700.0).flatMap { y ->
                listOf(250.0, 850.0).map { x -> BandFlow(40.0, 40.0, 0.9, x, y) }
            }
        val moves =
            syntheticMoves(cal, listOf(0.0, 90.0), listOf(900.0, 1000.0, 1200.0, 1350.0))
                .map { m -> LegMove(m.d, m.bands + garbage) }
        val residuals = GroundMapFit.moveResidualsPx(cal, moves, 1080, 2340).filterNotNull()
        assertEquals(moves.size, residuals.size)
        assertEquals(0.0, residuals.max(), 0.5)
    }

    @Test
    fun nonFiniteInputNeverYieldsNonFiniteFit() {
        val stored = GroundMapFit.fit(moves("A"), 540.0, 1510.0, 1080, 2340)!!.calibration
        val huge = syntheticMoves(stored, listOf(0.0, 90.0)).map { m -> LegMove(m.d, m.bands.map { it.copy(fx = 1e200, fy = 1e200) }) }
        val fit = GroundMapFit.fit(huge, 540.0, 1510.0, 1080, 2340) ?: return
        val c = fit.calibration
        for (v in listOf(c.headingDeg, c.lateralPxPerM, c.forwardPxPerM, c.perspective, fit.medianAbsPx)) assertTrue("$v", v.isFinite())
    }

    @Test
    fun emptyMovesReturnsNull() {
        assertNull(GroundMapFit.fit(emptyList(), 540.0, 1510.0, 1080, 2340))
    }

    @Test
    fun horizonRefitDropsBandsAboveTheHorizon() {
        val forward = 3.0
        val cal = GroundCalibration(0.0, 7.0, forward, forward / 702.0, 540.0, 1510.0)
        val garbage =
            listOf(100.0, 200.0, 300.0, 500.0, 700.0).flatMap { y ->
                listOf(250.0, 850.0).map { x -> BandFlow(40.0, 40.0, 0.9, x, y) }
            }
        val moves =
            syntheticMoves(cal, listOf(0.0, 90.0), listOf(900.0, 1000.0, 1200.0, 1350.0))
                .map { m -> LegMove(m.d, m.bands + garbage) }
        val fit = GroundMapFit.fit(moves, 540.0, 1510.0, 1080, 2340)!!
        assertTrue("median ${fit.medianAbsPx}", fit.medianAbsPx < 1.0)
        assertEquals(0.0, fit.calibration.headingDeg, 1.0)
    }

    @Test
    fun fitMedianMatchesMoveResidualsOnTheSameFlows() {
        // the golden file holds one band per move; group them so each move has the 3 bands a residual needs
        val moves = moves("A").groupBy { it.d }.map { (d, ms) -> LegMove(d, ms.flatMap { it.bands }) }
        val fit = GroundMapFit.fit(moves, 540.0, 1510.0, 1080, 2340)!!
        val residuals = GroundMapFit.moveResidualsPx(fit.calibration, moves, 1080, 2340).filterNotNull().sorted()
        val median = (residuals[(residuals.size - 1) / 2] + residuals[residuals.size / 2]) / 2
        assertEquals(fit.medianAbsPx, median, 1.0)
    }
}
