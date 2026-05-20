package com.locationjoystick.core.testing

import com.locationjoystick.core.database.dao.WaypointDao
import com.locationjoystick.core.database.entities.WaypointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeWaypointDao : WaypointDao {
    private val state = MutableStateFlow<List<WaypointEntity>>(emptyList())

    override suspend fun insert(waypoint: WaypointEntity) {
        state.value = state.value.filter { it.id != waypoint.id } + waypoint
    }

    override suspend fun insertAll(waypoints: List<WaypointEntity>) {
        val incoming = waypoints.map { it.id }.toSet()
        state.value = state.value.filter { it.id !in incoming } + waypoints
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filter { it.id != id }
    }

    override suspend fun deleteByRouteId(routeId: String) {
        state.value = state.value.filter { it.routeId != routeId }
    }

    override fun getByRouteId(routeId: String): Flow<List<WaypointEntity>> =
        state.map { list -> list.filter { it.routeId == routeId }.sortedBy { it.orderIndex } }

    fun snapshot(): List<WaypointEntity> = state.value

    fun deleteAll() {
        state.value = emptyList()
    }
}
