package com.locationjoystick.core.map.geojson

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.advancePosition
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.model.LatLng

fun emptyGeoJson(): String = """{"type":"FeatureCollection","features":[]}"""

fun buildPositionGeoJson(position: LatLng?): String =
    if (position != null) {
        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":""" +
            """{"type":"Point","coordinates":[${position.longitude},${position.latitude}]},"properties":{}}]}"""
    } else {
        emptyGeoJson()
    }

/**
 * Splits [waypoints] into a traced segment (up to the closest point to [currentPosition])
 * and a remaining segment (from current position onward).
 *
 * Uses Haversine distance — fixes the squared Euclidean degrees bug in MapFloatingView.
 */
fun buildRouteTraceGeoJson(
    waypoints: List<LatLng>,
    currentPosition: LatLng,
): Pair<String, String> {
    if (waypoints.isEmpty()) return emptyGeoJson() to emptyGeoJson()

    var closestIdx = 0
    var minDist = Double.MAX_VALUE
    for (i in waypoints.indices) {
        val dist =
            haversineDistance(
                currentPosition.latitude,
                currentPosition.longitude,
                waypoints[i].latitude,
                waypoints[i].longitude,
            )
        if (dist < minDist) {
            minDist = dist
            closestIdx = i
        }
    }

    val tracedPoints = (0..closestIdx).map { waypoints[it] } + currentPosition
    val remainingPoints = listOf(currentPosition) + waypoints.drop(closestIdx + 1)

    return buildLineGeoJson(tracedPoints) to buildLineGeoJson(remainingPoints)
}

fun buildLineGeoJson(points: List<LatLng>): String {
    if (points.size < 2) return emptyGeoJson()
    val coords = points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    val feature = """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}"""
    return """{"type":"FeatureCollection","features":[$feature]}"""
}

fun buildPointsGeoJson(points: List<LatLng>): String {
    if (points.isEmpty()) return emptyGeoJson()
    val features =
        points.joinToString(",") {
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[${it.longitude},${it.latitude}]},"properties":{}}"""
        }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

fun buildMarkerGeoJson(
    lat: Double,
    lon: Double,
): String =
    """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{}}]}"""

fun buildSegmentsGeoJson(segments: List<List<LatLng>>): String {
    if (segments.isEmpty()) return emptyGeoJson()
    val features =
        segments.map { segment ->
            val coordinates = segment.map { listOf(it.longitude, it.latitude) }
            """{"type":"Feature","geometry":{"type":"LineString","coordinates":$coordinates},"properties":{}}"""
        }
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

/**
 * Builds a GeoJSON Polygon approximating a circle of [radiusMeters] around
 * [center], using [segments] points on the perimeter (real-world meters via
 * [advancePosition], not screen pixels — see docs/features/map.md, "Jitter
 * Radius Overlay"). Returns [emptyGeoJson] when [radiusMeters] is not positive.
 */
fun buildCirclePolygonGeoJson(
    center: LatLng,
    radiusMeters: Double,
    segments: Int = AppConstants.MapConstants.JITTER_RADIUS_CIRCLE_SEGMENTS,
): String {
    if (radiusMeters <= 0.0) return emptyGeoJson()
    val ring =
        (0..segments).joinToString(",") { i ->
            val bearing = 360.0 * i / segments
            val (lat, lon) = advancePosition(center.latitude, center.longitude, bearing, radiusMeters)
            "[$lon,$lat]"
        }
    val feature = """{"type":"Feature","geometry":{"type":"Polygon","coordinates":[[$ring]]},"properties":{}}"""
    return """{"type":"FeatureCollection","features":[$feature]}"""
}

fun buildWaypointsGeoJson(waypoints: List<LatLng>): String {
    if (waypoints.isEmpty()) return emptyGeoJson()
    val features =
        waypoints.map { wp ->
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[${wp.longitude},${wp.latitude}]},"properties":{}}"""
        }
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}
