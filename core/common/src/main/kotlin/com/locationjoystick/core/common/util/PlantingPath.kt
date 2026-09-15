package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import kotlin.math.PI
import kotlin.math.roundToInt

fun clampPlantingRadius(radiusMeters: Double): Double =
    radiusMeters.coerceIn(
        AppConstants.RouteConstants.PLANTING_MIN_RADIUS_METERS,
        AppConstants.RouteConstants.PLANTING_MAX_RADIUS_METERS,
    )

fun plantingCircleVertexCount(radiusMeters: Double): Int {
    val circumference = 2.0 * PI * radiusMeters
    val raw = (circumference / AppConstants.RouteConstants.PLANTING_CHORD_METERS).roundToInt()
    return raw.coerceIn(
        AppConstants.RouteConstants.PLANTING_MIN_VERTICES,
        AppConstants.RouteConstants.PLANTING_MAX_VERTICES,
    )
}

/**
 * Nearest-neighbor order starting at the point with the smallest lat+lon.
 */
fun orderByProximity(points: List<LatLng>): List<LatLng> {
    if (points.size <= 2) return points.toList()
    val unvisited = points.toMutableList()
    var startIdx = 0
    var minVal = Double.POSITIVE_INFINITY
    unvisited.forEachIndexed { index, point ->
        val value = point.latitude + point.longitude
        if (value < minVal) {
            minVal = value
            startIdx = index
        }
    }
    val sorted = mutableListOf(unvisited.removeAt(startIdx))
    while (unvisited.isNotEmpty()) {
        val last = sorted.last()
        var bestIndex = 0
        var bestDistance = Double.POSITIVE_INFINITY
        unvisited.forEachIndexed { index, point ->
            val distance = haversineDistance(last, point)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        sorted += unvisited.removeAt(bestIndex)
    }
    return sorted
}

/**
 * Closed clockwise ring around [center]. The center itself is not a waypoint.
 * First vertex is due north of the center.
 */
fun circleAround(
    center: LatLng,
    radiusMeters: Double,
): List<LatLng> {
    val radius = clampPlantingRadius(radiusMeters)
    val vertexCount = plantingCircleVertexCount(radius)
    val points =
        (0 until vertexCount).map { index ->
            val bearing = index * AppConstants.LocationConstants.DEGREES_IN_CIRCLE / vertexCount
            val (lat, lon) = advancePosition(center.latitude, center.longitude, bearing, radius)
            LatLng(lat, lon)
        }
    return points + points.first()
}

fun rotateClosedRingToNearest(
    ring: List<LatLng>,
    target: LatLng,
): List<LatLng> {
    if (ring.size < 2) return ring
    val unique = ring.dropLast(1)
    if (unique.isEmpty()) return ring
    var bestIndex = 0
    var bestDistance = Double.POSITIVE_INFINITY
    unique.forEachIndexed { index, point ->
        val distance = haversineDistance(target, point)
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    val rotated = unique.drop(bestIndex) + unique.take(bestIndex)
    return rotated + rotated.first()
}

fun plantingRings(
    centers: List<LatLng>,
    radiusMeters: Double,
): List<List<LatLng>> {
    if (centers.isEmpty()) return emptyList()
    val radius = clampPlantingRadius(radiusMeters)
    val rings = mutableListOf<List<LatLng>>()
    var previousExit: LatLng? = null
    for (center in centers) {
        var ring = circleAround(center, radius)
        val exit = previousExit
        if (exit != null) {
            ring = rotateClosedRingToNearest(ring, exit)
        }
        rings += ring
        previousExit = ring.last()
    }
    return rings
}

/**
 * One chained path: closed circle around each center, connected by straight lines.
 */
fun buildPlantingWaypoints(
    centers: List<LatLng>,
    radiusMeters: Double,
): List<LatLng> = buildPlantingReplayPath(centers, radiusMeters).first

fun stitchRingsWithConnectors(
    rings: List<List<LatLng>>,
    connectors: List<List<LatLng>>,
): List<LatLng> = stitchRingsWithConnectorsAndBoundaries(rings, connectors).first

/**
 * Concatenate closed rings with no connector geometry. Each ring's first vertex is a
 * boundary so replay can hop from one walk-around to the next instead of interpolating.
 */
fun stitchRingsWithoutConnectors(rings: List<List<LatLng>>): Pair<List<LatLng>, List<Int>> {
    if (rings.isEmpty()) return emptyList<LatLng>() to emptyList()
    val out = mutableListOf<LatLng>()
    val boundaries = mutableListOf<Int>()
    for (ring in rings) {
        if (ring.isEmpty()) continue
        boundaries += out.size
        out += ring
    }
    return out to boundaries
}

/**
 * Same geometry as [stitchRingsWithConnectors], plus the index of each ring's first vertex
 * in the flattened path so jump-to-stop can target real stops, not circle vertices.
 */
fun stitchRingsWithConnectorsAndBoundaries(
    rings: List<List<LatLng>>,
    connectors: List<List<LatLng>>,
): Pair<List<LatLng>, List<Int>> {
    if (rings.isEmpty()) return emptyList<LatLng>() to emptyList()
    val out = rings.first().toMutableList()
    val boundaries = mutableListOf(0)
    for (index in connectors.indices) {
        val connector = connectors[index]
        if (connector.size >= 2) {
            out += connector.drop(1)
        }
        val nextRing = rings.getOrNull(index + 1) ?: continue
        boundaries += out.lastIndex
        if (nextRing.size >= 2) {
            out += nextRing.drop(1)
        }
    }
    return out to boundaries
}

/**
 * Closed circles around each center, optionally joined by caller-supplied connectors.
 * Straight two-point connectors are used when [connectors] is null.
 *
 * @return flattened replay path and the index of each ring's first vertex.
 */
fun buildPlantingReplayPath(
    centers: List<LatLng>,
    radiusMeters: Double,
    connectors: List<List<LatLng>>? = null,
): Pair<List<LatLng>, List<Int>> {
    val rings = plantingRings(centers, radiusMeters)
    if (rings.isEmpty()) return emptyList<LatLng>() to emptyList()
    if (rings.size == 1) return rings[0] to listOf(0)
    val resolved =
        connectors ?: rings.zipWithNext { from, to ->
            listOf(from.last(), to.first())
        }
    return stitchRingsWithConnectorsAndBoundaries(rings, resolved)
}

fun flattenRouteLegs(legs: List<List<LatLng>>): List<LatLng> {
    if (legs.isEmpty()) return emptyList()
    return legs.flatMapIndexed { index, leg -> if (index == 0) leg else leg.drop(1) }
}
