package com.locationjoystick.core.location

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Owns a persistent position-jitter offset that wanders inside a max-deviation disc, stepping by
 * up to [maxStepMeters] in a fresh random direction every tick and clamping back to the disc edge
 * on overshoot — replaces the original interval-gated single Gaussian draw (issue #60, which
 * teleported to a point and back) and the first random-walk fix (which beelined to one distant
 * target per leg, reading as a DVD-logo bounce off the deviation radius — issue #60 follow-up).
 * Because the heading is redrawn every tick instead of held until a target is reached, this only
 * changes how the *jitter offset* wanders around the real anchor — it has no bearing on how the
 * anchor itself moves (joystick drag, walk-to, route replay all set that independently).
 */
internal class PositionJitterCoordinator {
    @Volatile private var offsetNorthM: Double = 0.0

    @Volatile private var offsetEastM: Double = 0.0

    /** Zeroes the offset — called from `startSpoofing()` so a new session starts unjittered. */
    fun reset() {
        offsetNorthM = 0.0
        offsetEastM = 0.0
    }

    /**
     * Steps the offset one tick in a uniformly random direction, clamped so it never exceeds
     * [radiusMeters] (or reset to zero, if [radiusMeters] is non-positive — jitter disabled).
     * [bearingDeg]/[longitudinalFraction] squish the deviation disc into an ellipse along the
     * direction of travel, same convention as [gaussianLatLonOffsetLateral]: the offset is worked
     * out in that ellipse's own (lateral, longitudinal) frame, un-squished to a circle for the
     * step + clamp math, then squished back.
     */
    fun step(
        radiusMeters: Double,
        maxStepMeters: Double,
        bearingDeg: Float,
        longitudinalFraction: Double,
        random: Random,
    ): Pair<Double, Double> {
        if (radiusMeters <= 0.0) {
            reset()
            return Pair(0.0, 0.0)
        }

        val bearingRad = Math.toRadians(bearingDeg.toDouble())
        val northFwd = cos(bearingRad)
        val eastFwd = sin(bearingRad)
        val northLat = -sin(bearingRad)
        val eastLat = cos(bearingRad)

        val lateral = offsetNorthM * northLat + offsetEastM * eastLat
        val longitudinal = offsetNorthM * northFwd + offsetEastM * eastFwd
        // Guards only the division below — a zero fraction is a legitimate degenerate ellipse
        // (a flat line with no longitudinal extent at all), squished back out via the real
        // longitudinalFraction (not this guarded copy) once the step below is computed.
        val divisionSafeFraction = if (longitudinalFraction > 0.0) longitudinalFraction else 1.0

        // Circle space: undo the ellipse squish so step + clamp can treat the boundary as a plain circle.
        var cx = lateral
        var cy = longitudinal / divisionSafeFraction

        val theta = 2.0 * Math.PI * random.nextDouble()
        cx += maxStepMeters * cos(theta)
        cy += maxStepMeters * sin(theta)

        val dist = hypot(cx, cy)
        if (dist > radiusMeters) {
            val scale = radiusMeters / dist
            cx *= scale
            cy *= scale
        }

        val newLongitudinal = cy * longitudinalFraction
        offsetNorthM = cx * northLat + newLongitudinal * northFwd
        offsetEastM = cx * eastLat + newLongitudinal * eastFwd
        return Pair(offsetNorthM, offsetEastM)
    }
}
