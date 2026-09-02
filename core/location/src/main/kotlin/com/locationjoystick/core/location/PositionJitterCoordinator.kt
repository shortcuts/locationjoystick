package com.locationjoystick.core.location

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Owns a persistent position-jitter offset that walks toward a randomly chosen target inside a
 * max-deviation disc, stepping at most [maxStepMeters] per tick — replaces the previous
 * interval-gated single Gaussian draw (issue #60), which held the anchor exact between fires and
 * then teleported to a point and back the very next tick.
 */
internal class PositionJitterCoordinator {
    @Volatile private var offsetNorthM: Double = 0.0

    @Volatile private var offsetEastM: Double = 0.0

    @Volatile private var targetNorthM: Double = 0.0

    @Volatile private var targetEastM: Double = 0.0

    /** Zeroes the offset — called from `startSpoofing()` so a new session starts unjittered. */
    fun reset() {
        offsetNorthM = 0.0
        offsetEastM = 0.0
        targetNorthM = 0.0
        targetEastM = 0.0
    }

    /**
     * Steps the offset one tick toward the current target, drawing a fresh target within
     * [radiusMeters] whenever the previous one is reached (or immediately, if [radiusMeters] is
     * non-positive — jitter disabled). [bearingDeg]/[longitudinalFraction] squish the target disc
     * along the direction of travel, same convention as [gaussianLatLonOffsetLateral].
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
        val distToTarget = hypot(targetNorthM - offsetNorthM, targetEastM - offsetEastM)
        if (distToTarget <= maxStepMeters) {
            offsetNorthM = targetNorthM
            offsetEastM = targetEastM
            val (n, e) = randomTargetInDisc(radiusMeters, bearingDeg, longitudinalFraction, random)
            targetNorthM = n
            targetEastM = e
        } else {
            val ratio = maxStepMeters / distToTarget
            offsetNorthM += (targetNorthM - offsetNorthM) * ratio
            offsetEastM += (targetEastM - offsetEastM) * ratio
        }
        return Pair(offsetNorthM, offsetEastM)
    }
}

/**
 * Picks a uniformly-random point inside an ellipse: full [radiusMeters] laterally to
 * [bearingDeg], squished to `radiusMeters * longitudinalFraction` along it. Uniform-in-disc (not
 * Gaussian) so the result never exceeds the configured max deviation — issue #60's explicit ask.
 * bearingDeg: 0 = North, 90 = East (Android convention, matches [gaussianLatLonOffsetLateral]).
 */
internal fun randomTargetInDisc(
    radiusMeters: Double,
    bearingDeg: Float,
    longitudinalFraction: Double,
    random: Random,
): Pair<Double, Double> {
    val r = radiusMeters * sqrt(random.nextDouble())
    val theta = 2.0 * Math.PI * random.nextDouble()
    val lateral = r * cos(theta)
    val longitudinal = r * sin(theta) * longitudinalFraction

    val bearingRad = Math.toRadians(bearingDeg.toDouble())
    val northFwd = cos(bearingRad)
    val eastFwd = sin(bearingRad)
    val northLat = -sin(bearingRad)
    val eastLat = cos(bearingRad)

    return Pair(
        lateral * northLat + longitudinal * northFwd,
        lateral * eastLat + longitudinal * eastFwd,
    )
}
