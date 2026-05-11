package com.locationjoystick.core.model

enum class RouteType { STRAIGHT, GUIDED }

data class Route(
    val id: String,
    val name: String,
    val waypoints: List<Waypoint> = emptyList(),
    val isLooping: Boolean = false,
    val routeType: RouteType = RouteType.STRAIGHT,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
