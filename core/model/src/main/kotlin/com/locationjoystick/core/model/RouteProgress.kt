package com.locationjoystick.core.model

/**
 * 1-based progress through a route's named stops, shown as [label] (`current/total`).
 *
 * [current] is the last named stop reached (starting at 1). [total] is the number of
 * named stops, not expanded road or planting-circle vertices.
 */
data class RouteProgress(
    val current: Int,
    val total: Int,
) {
    val label: String get() = "$current/$total"
}

/**
 * Derives [RouteProgress] from the replay engine's next-waypoint pointer and the named-stop
 * boundary list.
 *
 * [resumeWaypointIndex] is the expanded-path index currently being walked toward (1 at start).
 * [boundaryIndices] are the expanded-path indices of named stops. Empty [boundaryIndices]
 * means no replay is active.
 */
fun computeRouteProgress(
    resumeWaypointIndex: Int,
    boundaryIndices: List<Int>,
): RouteProgress? {
    if (boundaryIndices.isEmpty()) return null
    val total = boundaryIndices.size
    val lastReached = (resumeWaypointIndex - 1).coerceAtLeast(0)
    val current = boundaryIndices.count { it <= lastReached }.coerceIn(1, total)
    return RouteProgress(current, total)
}
