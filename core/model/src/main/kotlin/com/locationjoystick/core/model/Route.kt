package com.locationjoystick.core.model

enum class RouteType { STRAIGHT, GUIDED, TELEPORT }

data class Route(
    val id: String,
    val name: String,
    val waypoints: List<Waypoint> = emptyList(),
    val isLooping: Boolean = false,
    val routeType: RouteType = RouteType.STRAIGHT,
    val speedProfileId: String? = null,
    /** TELEPORT routes only: shuffle waypoint order on start and again at each loop restart. */
    val randomizeTeleportOrder: Boolean = false,
    /** Non-null = Planting route: replay circles each waypoint at this radius; waypoints are centers. */
    val plantingRadiusMeters: Double? = null,
    override val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** User group name; null = ungrouped. Hot routes (`hot_route_` ids) are never grouped. */
    val category: String? = null,
) : HasCreatedAt

/** Waypoint replay starts (and Teleport jumps to): first, or last when [isReverse]. */
fun Route.startWaypoint(isReverse: Boolean): Waypoint? = if (isReverse) waypoints.lastOrNull() else waypoints.firstOrNull()
