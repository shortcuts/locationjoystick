package com.locationjoystick.core.common.util

import com.locationjoystick.core.model.LatLng
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)

    val a =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
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
 * Calculates the initial compass bearing from [from] to [to].
 * @return bearing in degrees [0, 360)
 */
fun bearingBetweenCoords(
    from: LatLng,
    to: LatLng,
): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)

    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val bearing = Math.toDegrees(atan2(y, x))
    return ((bearing + 360.0) % 360.0).toFloat()
}

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
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
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
    val latRad = Math.toRadians(lat)
    val lonRad = Math.toRadians(lon)
    val bearing = Math.toRadians(bearingDeg)
    val angularDist = distanceM / EARTH_RADIUS_METERS
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
    return Math.toDegrees(newLatRad) to Math.toDegrees(newLonRad)
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
    val lat0 = Math.toRadians(lineStart.latitude)
    val x2 = Math.toRadians(lineEnd.longitude - lineStart.longitude) * cos(lat0) * EARTH_RADIUS_METERS
    val y2 = Math.toRadians(lineEnd.latitude - lineStart.latitude) * EARTH_RADIUS_METERS
    val px = Math.toRadians(point.longitude - lineStart.longitude) * cos(lat0) * EARTH_RADIUS_METERS
    val py = Math.toRadians(point.latitude - lineStart.latitude) * EARTH_RADIUS_METERS
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
    val step = 45f
    return (kotlin.math.round(bearing / step) * step % 360f)
}
