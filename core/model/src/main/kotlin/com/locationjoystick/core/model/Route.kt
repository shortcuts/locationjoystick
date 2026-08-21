package com.locationjoystick.core.model

enum class RouteType { STRAIGHT, GUIDED }

data class Route(
    val id: String,
    val name: String,
    val waypoints: List<Waypoint> = emptyList(),
    val isLooping: Boolean = false,
    val routeType: RouteType = RouteType.STRAIGHT,
    val speedProfileId: String? = null,
    override val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) : HasCreatedAt

/** Waypoint replay starts (and Teleport jumps to): first, or last when [isReverse]. */
fun Route.startWaypoint(isReverse: Boolean): Waypoint? = if (isReverse) waypoints.lastOrNull() else waypoints.firstOrNull()
