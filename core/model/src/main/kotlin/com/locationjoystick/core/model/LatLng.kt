package com.locationjoystick.core.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.toRadians

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
    val lat1 = latitude.toRadians()
    val lat2 = other.latitude.toRadians()
    val dLat = (other.latitude - latitude).toRadians()
    val dLon = (other.longitude - longitude).toRadians()

    val a =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return 6_371_000.0 * c
}
