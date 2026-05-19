package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private fun Double.toRadians(): Double = Math.toRadians(this)

private fun Double.toDegrees(): Double = Math.toDegrees(this)

/**
 * Calculates the great-circle distance between two points using the Haversine formula.
 * @return distance in meters
 */
fun haversineDistance(
    from: LatLng,
    to: LatLng,
): Double {
    val dLat = (to.latitude - from.latitude).toRadians()
    val dLon = (to.longitude - from.longitude).toRadians()
    val lat1 = from.latitude.toRadians()
    val lat2 = to.latitude.toRadians()

    val a =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * AppConstants.LocationConstants.EARTH_RADIUS_METERS * asin(sqrt(a))
}

/**
 * Calculates the great-circle distance between two points using raw coordinates.
 * @return distance in meters
 */
fun haversineDistance(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double = haversineDistance(LatLng(lat1, lon1), LatLng(lat2, lon2))

/**
 * Calculates the initial compass bearing between two points given as raw coordinates.
 * @return bearing in degrees [0, 360)
 */
fun calculateBearing(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val dLon = (lon2 - lon1).toRadians()
    val lat1Rad = lat1.toRadians()
    val lat2Rad = lat2.toRadians()
    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    return (atan2(y, x).toDegrees() + AppConstants.LocationConstants.DEGREES_IN_CIRCLE) %
        AppConstants.LocationConstants.DEGREES_IN_CIRCLE
}

/**
 * Advances a position by [distanceM] meters along [bearingDeg] degrees.
 * @return new (latitude, longitude) pair
 */
fun advancePosition(
    lat: Double,
    lon: Double,
    bearingDeg: Double,
    distanceM: Double,
): Pair<Double, Double> {
    val latRad = lat.toRadians()
    val lonRad = lon.toRadians()
    val bearing = bearingDeg.toRadians()
    val angularDist = distanceM / AppConstants.LocationConstants.EARTH_RADIUS_METERS
    val newLatRad =
        asin(
            sin(latRad) * cos(angularDist) +
                cos(latRad) * sin(angularDist) * cos(bearing),
        )
    val newLonRad =
        lonRad +
            atan2(
                sin(bearing) * sin(angularDist) * cos(latRad),
                cos(angularDist) - sin(latRad) * sin(newLatRad),
            )
    return newLatRad.toDegrees() to newLonRad.toDegrees()
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
    val r = radiusMeters * sqrt(Random.nextDouble())
    val theta = Random.nextDouble() * 2 * PI
    val dLat = metersToLatDegrees(r * cos(theta))
    val dLng = metersToLngDegrees(r * sin(theta), center.latitude)
    return LatLng(center.latitude + dLat, center.longitude + dLng)
}

/**
 * Converts a distance in meters to degrees of latitude.
 */
fun metersToLatDegrees(meters: Double): Double = meters / AppConstants.LocationConstants.EARTH_RADIUS_METERS * (180.0 / PI)

/**
 * Converts a distance in meters to degrees of longitude at a given latitude.
 */
fun metersToLngDegrees(
    meters: Double,
    latitude: Double,
): Double {
    val latRad = latitude.toRadians()
    return meters / (AppConstants.LocationConstants.EARTH_RADIUS_METERS * cos(latRad)) * (180.0 / PI)
}

/**
 * Simplifies a path using the Ramer-Douglas-Peucker algorithm.
 * Points whose perpendicular distance from the simplified line is less than [epsilon] meters are dropped.
 */
fun rdpSimplify(
    points: List<LatLng>,
    epsilon: Double,
): List<LatLng> {
    if (points.size <= 2) return points.toList()
    val start = points.first()
    val end = points.last()
    var maxDist = 0.0
    var maxIndex = 0
    for (i in 1 until points.size - 1) {
        val dist = perpendicularDistanceMeters(points[i], start, end)
        if (dist > maxDist) {
            maxDist = dist
            maxIndex = i
        }
    }
    return if (maxDist > epsilon) {
        val left = rdpSimplify(points.subList(0, maxIndex + 1), epsilon)
        val right = rdpSimplify(points.subList(maxIndex, points.size), epsilon)
        left.dropLast(1) + right
    } else {
        listOf(start, end)
    }
}

private fun perpendicularDistanceMeters(
    point: LatLng,
    lineStart: LatLng,
    lineEnd: LatLng,
): Double {
    val lat0 = lineStart.latitude.toRadians()
    val x2 = (lineEnd.longitude - lineStart.longitude).toRadians() * cos(lat0) * AppConstants.LocationConstants.EARTH_RADIUS_METERS
    val y2 = (lineEnd.latitude - lineStart.latitude).toRadians() * AppConstants.LocationConstants.EARTH_RADIUS_METERS
    val px = (point.longitude - lineStart.longitude).toRadians() * cos(lat0) * AppConstants.LocationConstants.EARTH_RADIUS_METERS
    val py = (point.latitude - lineStart.latitude).toRadians() * AppConstants.LocationConstants.EARTH_RADIUS_METERS
    val lineLen2 = x2 * x2 + y2 * y2
    if (lineLen2 == 0.0) return haversineDistance(point, lineStart)
    val t = ((px * x2) + (py * y2)) / lineLen2
    val tClamped = t.coerceIn(0.0, 1.0)
    val projX = tClamped * x2
    val projY = tClamped * y2
    return sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY))
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
    val step = AppConstants.LocationConstants.CARDINAL_SNAP_STEP_DEGREES.toFloat()
    return (kotlin.math.round(bearing / step) * step % AppConstants.LocationConstants.DEGREES_IN_CIRCLE.toFloat())
}
