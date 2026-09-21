package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng

/**
 * True when [targetIndex] is a named-stop (or planting-ring) boundary other than the start.
 * Index 0 is the replay start — already teleported/walked to — so it is never a hop target.
 */
fun isNamedStopBoundary(
    targetIndex: Int,
    boundaryIndices: List<Int>,
): Boolean = targetIndex > 0 && boundaryIndices.contains(targetIndex)

/**
 * Hop to [targetIndex] instead of interpolating toward it when teleport-between-waypoints is on
 * and that index is a named stop / next ring start.
 */
fun shouldTeleportToTargetWaypoint(
    teleportBetweenWaypoints: Boolean,
    targetIndex: Int,
    boundaryIndices: List<Int>,
): Boolean = teleportBetweenWaypoints && isNamedStopBoundary(targetIndex, boundaryIndices)

/**
 * Leftover interpolator carry must not walk into the next named stop or planting ring.
 * Stay on the vertex just arrived at; the next tick hops.
 */
fun snapCarryIfCrossingBoundary(
    result: InterpolationResult,
    waypoints: List<LatLng>,
    teleportBetweenWaypoints: Boolean,
    boundaryIndices: List<Int>,
): InterpolationResult {
    if (!teleportBetweenWaypoints || result.reachedEnd) return result
    if (!isNamedStopBoundary(result.nextWaypointIndex, boundaryIndices)) return result
    val arrivedAt = result.nextWaypointIndex - 1
    if (arrivedAt !in waypoints.indices) return result
    return InterpolationResult(
        position = waypoints[arrivedAt],
        nextWaypointIndex = result.nextWaypointIndex,
        reachedEnd = false,
    )
}
