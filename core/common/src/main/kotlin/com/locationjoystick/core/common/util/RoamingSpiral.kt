package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import kotlin.math.PI
import kotlin.math.roundToInt

private val MIN_STEP_RADIANS = Math.toRadians(2.0)
private val MAX_STEP_RADIANS = Math.toRadians(30.0)

fun clampPlantingRoamingRadius(meters: Double): Double =
    meters.coerceIn(
        AppConstants.RoamingConstants.PLANTING_MIN_RADIUS_METERS,
        AppConstants.RoamingConstants.PLANTING_MAX_RADIUS_METERS,
    )

fun normalizePlantingRadii(
    startMeters: Double,
    endMeters: Double,
): Pair<Double, Double> {
    var start = clampPlantingRoamingRadius(startMeters)
    var end = clampPlantingRoamingRadius(endMeters)
    if (start >= end) {
        end =
            (start + AppConstants.RoamingConstants.PLANTING_PITCH_METERS)
                .coerceAtMost(AppConstants.RoamingConstants.PLANTING_MAX_RADIUS_METERS)
    }
    if (start >= end) {
        start =
            (end - AppConstants.RoamingConstants.PLANTING_PITCH_METERS)
                .coerceAtLeast(AppConstants.RoamingConstants.PLANTING_MIN_RADIUS_METERS)
    }
    return start to end
}

fun plantingSpiralRevolutions(
    startRadiusMeters: Double,
    endRadiusMeters: Double,
): Int {
    val span = kotlin.math.abs(endRadiusMeters - startRadiusMeters)
    val raw = (span / AppConstants.RoamingConstants.PLANTING_PITCH_METERS).roundToInt()
    return raw.coerceAtLeast(AppConstants.RoamingConstants.PLANTING_MIN_REVOLUTIONS)
}

/**
 * One closed planting loop around [center]: Archimedean spiral out from the start radius to the
 * end radius, then back in, continuing the same rotation so the last point sits on the start
 * radius at a multiple of 360°.
 */
fun buildPlantingSpiralLoop(
    center: LatLng,
    startRadiusMeters: Double,
    endRadiusMeters: Double,
): List<LatLng> {
    val (startR, endR) = normalizePlantingRadii(startRadiusMeters, endRadiusMeters)
    val revolutions = plantingSpiralRevolutions(startR, endR)
    val outbound = spiralArm(center, startR, endR, thetaStartRad = 0.0, revolutions = revolutions)
    val inbound =
        spiralArm(
            center,
            endR,
            startR,
            thetaStartRad = 2.0 * PI * revolutions,
            revolutions = revolutions,
        )
    return outbound + inbound.drop(1)
}

private fun spiralArm(
    center: LatLng,
    rStart: Double,
    rEnd: Double,
    thetaStartRad: Double,
    revolutions: Int,
): List<LatLng> {
    val totalTheta = 2.0 * PI * revolutions
    val points = mutableListOf<LatLng>()
    var theta = 0.0
    while (theta < totalTheta) {
        val t = theta / totalTheta
        val radius = rStart + (rEnd - rStart) * t
        points += pointAt(center, radius, thetaStartRad + theta)
        val step =
            (AppConstants.RoamingConstants.PLANTING_CHORD_METERS / radius.coerceAtLeast(1.0))
                .coerceIn(MIN_STEP_RADIANS, MAX_STEP_RADIANS)
        theta += step
    }
    points += pointAt(center, rEnd, thetaStartRad + totalTheta)
    return points
}

private fun pointAt(
    center: LatLng,
    radiusMeters: Double,
    bearingRad: Double,
): LatLng {
    val (lat, lon) =
        advancePosition(
            center.latitude,
            center.longitude,
            Math.toDegrees(bearingRad),
            radiusMeters,
        )
    return LatLng(lat, lon)
}
