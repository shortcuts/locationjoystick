package com.locationjoystick.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Waypoint

@Entity(
    tableName = "waypoints",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routeId")],
)
data class WaypointEntity(
    @PrimaryKey
    val id: String,
    val routeId: String,
    val latitude: Double,
    val longitude: Double,
    val orderIndex: Int,
)

fun WaypointEntity.toDomain(): Waypoint =
    Waypoint(
        id = id,
        position = LatLng(latitude = latitude, longitude = longitude),
        orderIndex = orderIndex,
    )

fun Waypoint.toEntity(routeId: String): WaypointEntity =
    WaypointEntity(
        id = id,
        routeId = routeId,
        latitude = position.latitude,
        longitude = position.longitude,
        orderIndex = orderIndex,
    )
