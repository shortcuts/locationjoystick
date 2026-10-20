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

/** Automatic fit analysis: feet pixel, band flows and fit, chained as in `registration_bench`. */
object GroundCalibrationSolver {
    // Reference px per metre below which the content cannot have followed the walk: real maps are 3 to 10,
    // a screen that did not scroll fits ~0.003 with a perfect residual.
    private const val MIN_SCALE_PX_PER_M = 1.0

    fun solve(
        legs: List<CapturedLeg>,
        width: Int,
        height: Int,
        checkCancelled: () -> Unit = {},
    ): SolveResult {
        val grads =
            legs.mapNotNull { leg ->
                checkCancelled()
                val moving = CalibrationWalkMath.movingFlags(leg.timesS, leg.positions)
                val frames = leg.frames()
                FeetDetector.legGradient(frames.filterIndexed { i, _ -> moving[i] })
            }
        if (grads.isEmpty()) return SolveResult.FeetNotFound
        val feet = FeetDetector.detectFromGradients(grads, width, height) ?: FeetDetector.fallback(width, height)
        val moves =
            legs.flatMap { leg ->
                checkCancelled()
                val frames = leg.frames()
                if (frames.isEmpty()) return@flatMap emptyList()
                val lat0 = leg.positions[0].latitude
                val offsets = leg.positions.map { CalibrationWalkMath.offset(leg.positions[0], it, lat0) }
                val walk = WalkLeg(frames, CalibrationWalkMath.movingFlags(leg.timesS, leg.positions), offsets)
                FrameFlow.bandMoves(listOf(walk), feet.x, feet.y)
            }
        val fit = GroundMapFit.fit(moves, feet.x, feet.y, width, height) ?: return SolveResult.TooFewFlows
        val c = fit.calibration
        if (minOf(abs(c.lateralPxPerM), abs(c.forwardPxPerM)) < MIN_SCALE_PX_PER_M) return SolveResult.ImplausibleFit
        if (c.lateralPxPerM * c.forwardPxPerM <= 0.0) return SolveResult.ImplausibleFit // a mirror image no camera produces
        return SolveResult.Ok(fit, feet)
    }
}
