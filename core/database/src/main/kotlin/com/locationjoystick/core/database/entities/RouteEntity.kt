package com.locationjoystick.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType

/**
 * Room entity for routes stored in the database.
 *
 * Each route has a one-to-many relationship with [WaypointEntity].
 * Use [toDomain] to convert to the domain model [Route].
 */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val isLooping: Boolean,
    val routeType: String = "STRAIGHT",
    val speedProfileId: String? = null,
    val randomizeTeleportOrder: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val category: String? = null,
    val plantingRadiusMeters: Double? = null,
)

/**
 * Converts this entity to a domain [Route] model.
 * @param waypoints List of waypoint entities to include in the route
 */
fun RouteEntity.toDomain(waypoints: List<WaypointEntity>): Route =
    Route(
        id = id,
        name = name,
        waypoints = waypoints.sortedBy { it.orderIndex }.map { it.toDomain() },
        isLooping = isLooping,
        routeType = RouteType.valueOf(routeType),
        speedProfileId = speedProfileId,
        randomizeTeleportOrder = randomizeTeleportOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        category = category,
        plantingRadiusMeters = plantingRadiusMeters,
    )

fun Route.toEntity(): RouteEntity =
    RouteEntity(
        id = id,
        name = name,
        isLooping = isLooping,
        routeType = routeType.name,
        speedProfileId = speedProfileId,
        randomizeTeleportOrder = randomizeTeleportOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        category = category,
        plantingRadiusMeters = plantingRadiusMeters,
    )
