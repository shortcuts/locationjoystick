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
     * [radiusMeters]. An offset already outside a shrunken radius (zero when jitter is off) eases
     * toward the disc by at most [maxStepMeters] per tick.
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
        val radius = radiusMeters.coerceAtLeast(0.0)
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

        val prevDist = hypot(cx, cy)
        if (prevDist > radius) {
            // Radius shrank under the offset: ease toward the disc instead of snapping onto it.
            val scale = maxOf(radius, prevDist - maxStepMeters) / prevDist
            cx *= scale
            cy *= scale
        } else {
            val theta = 2.0 * Math.PI * random.nextDouble()
            cx += maxStepMeters * cos(theta)
            cy += maxStepMeters * sin(theta)
            val dist = hypot(cx, cy)
            if (dist > radius) {
                val scale = radius / dist
                cx *= scale
                cy *= scale
            }
        }

        val newLongitudinal = cy * longitudinalFraction
        offsetNorthM = cx * northLat + newLongitudinal * northFwd
        offsetEastM = cx * eastLat + newLongitudinal * eastFwd
        return Pair(offsetNorthM, offsetEastM)
    }
}
