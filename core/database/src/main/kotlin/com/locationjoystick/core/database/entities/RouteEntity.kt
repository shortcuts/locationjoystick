package com.locationjoystick.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val isLooping: Boolean,
    val routeType: String = "STRAIGHT",
    val createdAt: Long,
    val updatedAt: Long,
)

fun RouteEntity.toDomain(waypoints: List<WaypointEntity>): Route =
    Route(
        id = id,
        name = name,
        waypoints = waypoints.sortedBy { it.orderIndex }.map { it.toDomain() },
        isLooping = isLooping,
        routeType = RouteType.valueOf(routeType),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Route.toEntity(): RouteEntity =
    RouteEntity(
        id = id,
        name = name,
        isLooping = isLooping,
        routeType = routeType.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
