package com.locationjoystick.core.model

import kotlin.math.abs

/**
 * One captured calibration leg. [frames] is a lambda so the solver loads one leg's frames at a time
 * (a full walk is 0.5 to 0.7 GB at 1080x2340); it is called more than once and must return the same frames.
 */
class CapturedLeg(
    val bearingDeg: Double,
    val timesS: DoubleArray,
    val positions: List<LatLng>,
    val frames: () -> List<GrayImage>,
)

sealed interface SolveResult {
    data class Ok(
        val fit: GroundFit,
        val feet: FeetPixel,
    ) : SolveResult

    data object FeetNotFound : SolveResult

    data object TooFewFlows : SolveResult

    /** A fit converged but its scales say the screen content did not follow the walk (e.g. a paused game). */
    data object ImplausibleFit : SolveResult
}

/** Calibration walk analysis: feet pixel, band flows and fit, chained as in `registration_bench`. */
object GroundCalibrationSolver {
    /** Steps reported to `onStep` (1-based): gradients, feet, flows, fit. */
    const val STEP_COUNT = 4

    // Reference px per metre below which the content cannot have followed the walk: real maps are 3 to 10,
    // a screen that did not scroll fits ~0.003 with a perfect residual.
    private const val MIN_SCALE_PX_PER_M = 1.0

    fun solve(
        legs: List<CapturedLeg>,
        width: Int,
        height: Int,
        onStep: (Int) -> Unit = {},
    ): SolveResult {
        onStep(1)
        val grads =
            legs.mapNotNull { leg ->
                val moving = CalibrationWalkMath.movingFlags(leg.timesS, leg.positions)
                val frames = leg.frames()
                FeetDetector.legGradient(frames.filterIndexed { i, _ -> moving[i] })
            }
        onStep(2)
        val feet = FeetDetector.detectFromGradients(grads, width, height) ?: return SolveResult.FeetNotFound
        onStep(3)
        val moves =
            legs.flatMap { leg ->
                val frames = leg.frames()
                if (frames.isEmpty()) return@flatMap emptyList()
                val lat0 = leg.positions[0].latitude
                val offsets =
                    leg.positions.map {
                        CalibrationWalkMath.alongTrackOffset(leg.positions[0], it, leg.bearingDeg, lat0)
                    }
                val walk = WalkLeg(frames, CalibrationWalkMath.movingFlags(leg.timesS, leg.positions), offsets)
                FrameFlow.bandMoves(listOf(walk), feet.x, feet.y)
            }
        onStep(4)
        val fit = GroundMapFit.fit(moves, feet.x, feet.y, width, height) ?: return SolveResult.TooFewFlows
        val c = fit.calibration
        if (minOf(abs(c.lateralPxPerM), abs(c.forwardPxPerM)) < MIN_SCALE_PX_PER_M) return SolveResult.ImplausibleFit
        return SolveResult.Ok(fit, feet)
    }
}
