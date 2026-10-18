package com.locationjoystick.core.model

import kotlin.math.abs

/** A straight stretch of samples (one frame each): [bearingDeg] runs from the first position to the last. */
class Leg(
    val timesS: DoubleArray,
    val positions: List<LatLng>,
    val bearingDeg: Double,
)

/**
 * Cuts a stream of (time, position) samples into straight legs. The positions are our own mock fixes, so a
 * leg's bearing and length are exact. A leg is closed when the player slows under [CalibrationWalkMath.MOVING_MIN_MPS],
 * when a step turns more than [MAX_TURN_DEG] away from the leg, or at [maxSamples]. Legs under [MIN_SAMPLES] samples
 * or [MIN_LENGTH_M] metres are dropped (no flow pair would pass `FrameFlow`'s minimum move).
 */
class LegSegmenter(
    private val maxSamples: Int = DEFAULT_MAX_SAMPLES,
) {
    private val times = ArrayList<Double>()
    private val positions = ArrayList<LatLng>()

    /** Samples in the open leg. */
    val size: Int get() = positions.size

    /** Adds a sample; returns the leg this sample closed, if any (the sample then opens the next one). */
    fun add(
        tS: Double,
        p: LatLng,
    ): Leg? {
        if (positions.isEmpty()) {
            append(tS, p)
            return null
        }
        val last = positions.last()
        val speed = last.distanceTo(p) / maxOf(tS - times.last(), 1e-6)
        val broken = speed < CalibrationWalkMath.MOVING_MIN_MPS || turn(p) > MAX_TURN_DEG
        if (broken) {
            val closed = close()
            append(tS, p)
            return closed
        }
        append(tS, p)
        return if (positions.size >= maxSamples) close() else null
    }

    /** Closes the open leg (a tick with no movement, the gate closing); null when it is too short to use. */
    fun close(): Leg? {
        val leg =
            if (positions.size >= MIN_SAMPLES && positions.first().distanceTo(positions.last()) >= MIN_LENGTH_M) {
                Leg(times.toDoubleArray(), positions.toList(), positions.first().bearingTo(positions.last()))
            } else {
                null
            }
        times.clear()
        positions.clear()
        return leg
    }

    private fun append(
        tS: Double,
        p: LatLng,
    ) {
        times += tS
        positions += p
    }

    /** Degrees between the step to [p] and the leg so far (first to last position; the last step while it is under 2 m). */
    private fun turn(p: LatLng): Double {
        val last = positions.last()
        val base = if (positions.first().distanceTo(last) >= MIN_LENGTH_M) positions.first() else positions.getOrNull(positions.size - 2)
        base ?: return 0.0
        val d = abs(base.bearingTo(last) - last.bearingTo(p)) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    companion object {
        const val MAX_TURN_DEG = 10.0
        const val MIN_SAMPLES = 6
        const val MIN_LENGTH_M = 2.0
        const val DEFAULT_MAX_SAMPLES = 24 // 72 s at the 3 s tick; bounds a leg's spool to about 60 MB and its analysis to the heap
    }
}
