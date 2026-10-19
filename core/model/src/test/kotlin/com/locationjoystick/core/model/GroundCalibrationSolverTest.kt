package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class GroundCalibrationSolverTest {
    private val lat0 = 45.0
    private val lon0 = 7.0

    // Per-frame content scroll (px) per leg; opposite legs differ in size so their frame sets are not identical.
    private val scrolls = listOf(0 to 9, 0 to -8, 9 to 0, -8 to 0, 6 to 6, -5 to -5, 6 to -6, -5 to 5)
    private val bearings = CalibrationWalkMath.BEARINGS_DEG

    /** Truth: feet px / m scales and heading; perspective 0 so the map is affine and invertible by 2x2. */
    private var truth = GroundCalibration(20.0, 7.0, 6.0, 0.0, 0.0, 0.0)

    private fun legs(
        w: Int,
        h: Int,
        framesLoaded: IntArray? = null,
        scene: SyntheticWalkScene = SyntheticWalkScene(w, h, 0.5 * w, 1500.0 * h / 2340, figure = true, contrast = 0.12),
    ): List<CapturedLeg> {
        val s = w / 1080.0
        val cal = truth.copy(feetX = 540.0, feetY = 1500.0, lateralPxPerM = truth.lateralPxPerM, forwardPxPerM = truth.forwardPxPerM)
        val map = GroundMap(cal, 1080.0, 2340.0)
        val feet = map.groundToPixel(0.0, 0.0)
        // content flow (reference px) of the player moving 1 m east / north
        val ex = map.groundToPixel(-1.0, 0.0).let { (it.first - feet.first) to (it.second - feet.second) }
        val ey = map.groundToPixel(0.0, -1.0).let { (it.first - feet.first) to (it.second - feet.second) }
        val det = ex.first * ey.second - ey.first * ex.second
        return scrolls.mapIndexed { idx, (dx, dy) ->
            // scroll (dx, dy) px/frame = content moving by (-dx, -dy) at this width = reference flow / s
            val fx = -dx / s
            val fy = -dy / s
            val east = (fx * ey.second - ey.first * fy) / det
            val north = (ex.first * fy - fx * ex.second) / det
            val cosLat = cos(Math.toRadians(lat0))
            val n = 9
            val positions =
                (0 until n).map {
                    LatLng(
                        lat0 + it * north / CalibrationWalkMath.METERS_PER_DEGREE,
                        lon0 + it * east / (CalibrationWalkMath.METERS_PER_DEGREE * cosLat),
                    )
                }
            // bearing of the real move; the solver projects onto the commanded bearing, so command it exactly
            val bearing = (Math.toDegrees(Math.atan2(east, north)) + 360.0) % 360.0
            CapturedLeg(
                bearing,
                DoubleArray(n) { it * 3.0 },
                positions,
            ) {
                framesLoaded?.set(idx, framesLoaded[idx] + 1)
                scene.leg(bearing, dx, dy, n).frames
            }
        }
    }

    private fun check(
        w: Int,
        h: Int,
    ) {
        val r = GroundCalibrationSolver.solve(legs(w, h), w, h)
        assertTrue("$r", r is SolveResult.Ok)
        r as SolveResult.Ok
        val c = r.fit.calibration
        assertEquals("heading", truth.headingDeg, c.headingDeg, 1.5)
        assertEquals("lateral", truth.lateralPxPerM, c.lateralPxPerM, 0.03 * truth.lateralPxPerM)
        // forward trades off against the fitted perspective term, so it gets a wider band than lateral
        assertEquals("forward", truth.forwardPxPerM, c.forwardPxPerM, 0.05 * truth.forwardPxPerM)
        assertEquals("feetX", 0.5 * w, r.feet.x, 0.01 * w)
        assertEquals("feetY", 1500.0 * h / 2340, r.feet.y, 0.02 * h)
    }

    @Test
    fun recoversTruthAtHalfSize() = check(540, 1170)

    @Test
    fun recoversTruthAtTwoThirdsSize() = check(720, 1600)

    @Test
    fun legsAreLoadedOneAtATimeAndRepeatedlyRequestedOnlyTwice() {
        val loaded = IntArray(8)
        GroundCalibrationSolver.solve(legs(540, 1170, loaded), 540, 1170)
        // once for the feet gradient, once for the flows
        assertEquals(List(8) { 2 }, loaded.toList())
    }

    @Test
    fun cancellationStopsTheSolveAtTheFirstLeg() {
        val loaded = IntArray(8)
        var checks = 0
        try {
            GroundCalibrationSolver.solve(legs(540, 1170, loaded), 540, 1170, checkCancelled = { if (++checks == 2) error("cancelled") })
            org.junit.Assert.fail("solve ignored the cancellation check")
        } catch (e: IllegalStateException) {
            assertEquals("cancelled", e.message)
        }
        assertEquals("only the first leg was read", 1, loaded.sum())
    }

    @Test
    fun playerNotMovingIsFeetNotFound() {
        val frame = SyntheticWalkScene(540, 1170, 270.0, 750.0, figure = true, contrast = 0.12).frame(0, 0, 0)
        val ls =
            bearings.map { b ->
                CapturedLeg(b, DoubleArray(9) { it * 3.0 }, List(9) { LatLng(lat0, lon0) }) { List(9) { frame } }
            }
        // never moving: no leg yields a gradient, so the walk fails at the feet step
        assertEquals(SolveResult.FeetNotFound, GroundCalibrationSolver.solve(ls, 540, 1170))
    }

    @Test
    fun worldThatNeverScrollsIsImplausibleFit() {
        val frame = SyntheticWalkScene(540, 1170, 270.0, 750.0, figure = true, contrast = 0.12).frame(0, 0, 0)
        // the position says the player walked, the screen did not change: all content is leg-static and cleaned
        val ls = legs(540, 1170).map { l -> CapturedLeg(l.bearingDeg, l.timesS, l.positions) { List(9) { frame } } }
        assertEquals(SolveResult.ImplausibleFit, GroundCalibrationSolver.solve(ls, 540, 1170))
    }

    @Test
    fun mirroredFitIsImplausible() {
        truth = truth.copy(forwardPxPerM = -truth.forwardPxPerM) // a mirror image no camera produces
        assertEquals(SolveResult.ImplausibleFit, GroundCalibrationSolver.solve(legs(540, 1170), 540, 1170))
    }

    @Test
    fun movingWorldWithoutFigureUsesPriorFeetAndFailsOnFlows() {
        val sc = SyntheticWalkScene(540, 1170, 270.0, 750.0, figure = false)
        val ls =
            legs(540, 1170, scene = sc).map { l ->
                CapturedLeg(l.bearingDeg, l.timesS, l.positions) {
                    // a flat grey world: no sharp static figure, so the prior feet row is used; no content, no flow
                    List(l.timesS.size) { GrayImage(540, 1170, ByteArray(540 * 1170) { 100 }) }
                }
            }
        assertEquals(SolveResult.TooFewFlows, GroundCalibrationSolver.solve(ls, 540, 1170))
    }

    @Test
    fun reportsStepsInOrder() {
        val steps = mutableListOf<Int>()
        GroundCalibrationSolver.solve(legs(540, 1170), 540, 1170) { steps += it }
        assertEquals(listOf(1, 2, 3, 4), steps)
        assertEquals(GroundCalibrationSolver.STEP_COUNT, steps.size)
    }
}
