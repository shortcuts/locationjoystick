package com.locationjoystick.core.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Immutable geographic coordinate.
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        // Cannot use AppConstants — core:model is a pure JVM module with no core:common dependency.
        // Keep in sync with AppConstants.LocationConstants.EARTH_RADIUS_METERS.
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

/**
 * Haversine distance in meters between two coordinates.
 */
fun LatLng.distanceTo(other: LatLng): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLat = Math.toRadians(other.latitude - latitude)
    val dLon = Math.toRadians(other.longitude - longitude)

    val a =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return 6_371_000.0 * c
}

/**
 * Initial bearing in degrees (0–360) from this coordinate to [other].
 */
fun LatLng.bearingTo(other: LatLng): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLon = Math.toRadians(other.longitude - longitude)

    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}
