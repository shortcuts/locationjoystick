package com.locationjoystick.core.common.util

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Data class representing a geographic coordinate.
 * Pure Kotlin — no android.location imports.
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

private const val EARTH_RADIUS_METERS = 6371000.0

/**
 * Calculates the great-circle distance between two points using the Haversine formula.
 * @return distance in meters
 */
fun haversineDistance(
    from: LatLng,
    to: LatLng,
): Double {
    val dLat = Math.toRadians(to.latitude - from.latitude)
    val dLng = Math.toRadians(to.longitude - from.longitude)
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)

    val a =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
}

/**
 * Calculates the initial compass bearing from [from] to [to].
 * @return bearing in degrees [0, 360)
 */
fun bearingBetween(
    from: LatLng,
    to: LatLng,
): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLng = Math.toRadians(to.longitude - from.longitude)

    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    val bearing = Math.toDegrees(atan2(y, x))
    return ((bearing + 360.0) % 360.0).toFloat()
}

/**
 * Linearly interpolates between two geographic positions.
 * @param fraction value in [0.0, 1.0]
 */
fun interpolatePosition(
    from: LatLng,
    to: LatLng,
    fraction: Double,
): LatLng {
    val lat = from.latitude + (to.latitude - from.latitude) * fraction
    val lng = from.longitude + (to.longitude - from.longitude) * fraction
    return LatLng(lat, lng)
}

/**
 * Returns a uniformly random point within [radiusMeters] of [center].
 */
fun randomPointInRadius(
    center: LatLng,
    radiusMeters: Double,
): LatLng {
    val r = radiusMeters * sqrt(Math.random())
    val theta = Math.random() * 2 * PI
    val dLat = metersToLatDegrees(r * cos(theta))
    val dLng = metersToLngDegrees(r * sin(theta), center.latitude)
    return LatLng(center.latitude + dLat, center.longitude + dLng)
}

/**
 * Converts a distance in meters to degrees of latitude.
 */
fun metersToLatDegrees(meters: Double): Double = meters / EARTH_RADIUS_METERS * (180.0 / PI)

/**
 * Converts a distance in meters to degrees of longitude at a given latitude.
 */
fun metersToLngDegrees(
    meters: Double,
    latitude: Double,
): Double {
    val latRad = Math.toRadians(latitude)
    return meters / (EARTH_RADIUS_METERS * cos(latRad)) * (180.0 / PI)
}

/**
 * Adds small random GPS jitter to a position for realism.
 * @param maxJitterMeters maximum jitter radius in meters (default 1.5m)
 */
fun addGpsJitter(
    position: LatLng,
    maxJitterMeters: Double = 1.5,
): LatLng = randomPointInRadius(position, maxJitterMeters)

/**
 * Optionally snaps a bearing to the nearest of 8 cardinal/intercardinal directions.
 * @param snap if false, returns [bearing] unchanged
 */
fun snapBearingToCardinal(
    bearing: Float,
    snap: Boolean,
): Float {
    if (!snap) return bearing
    val step = 45f
    return (kotlin.math.round(bearing / step) * step % 360f)
}
