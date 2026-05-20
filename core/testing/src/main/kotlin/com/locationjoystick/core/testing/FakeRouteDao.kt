package com.locationjoystick.core.testing

import com.locationjoystick.core.database.dao.RouteDao
import com.locationjoystick.core.database.dao.RouteWithWaypoints
import com.locationjoystick.core.database.entities.RouteEntity
import com.locationjoystick.core.database.entities.WaypointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRouteDao(
    private val waypointDao: FakeWaypointDao = FakeWaypointDao(),
) : RouteDao {
    private val routeState = MutableStateFlow<List<RouteEntity>>(emptyList())

    override suspend fun insert(route: RouteEntity) {
        routeState.value = routeState.value.filter { it.id != route.id } + route
    }

    override suspend fun update(route: RouteEntity) {
        routeState.value = routeState.value.map { if (it.id == route.id) route else it }
    }

    override suspend fun delete(route: RouteEntity) {
        routeState.value = routeState.value.filter { it.id != route.id }
    }

    override suspend fun getById(id: String): RouteEntity? = routeState.value.find { it.id == id }

    override fun getAll(): Flow<List<RouteEntity>> = routeState.map { list -> list.sortedByDescending { it.createdAt } }

    override fun getWithWaypoints(routeId: String): Flow<RouteWithWaypoints?> =
        routeState.map { routes ->
            routes.find { it.id == routeId }?.let { route ->
                RouteWithWaypoints(route, waypointDao.snapshot().filter { it.routeId == routeId })
            }
        }

    override fun getAllWithWaypoints(): Flow<List<RouteWithWaypoints>> =
        routeState.map { routes ->
            val allWaypoints = waypointDao.snapshot()
            routes
                .sortedByDescending { it.createdAt }
                .map { route -> RouteWithWaypoints(route, allWaypoints.filter { it.routeId == route.id }) }
        }

    override suspend fun deleteWaypointsByRouteId(routeId: String) {
        waypointDao.deleteByRouteId(routeId)
    }

    override suspend fun insertWaypoints(waypoints: List<WaypointEntity>) {
        waypointDao.insertAll(waypoints)
    }

    override suspend fun deleteWaypointById(waypointId: String) {
        waypointDao.delete(waypointId)
    }
}
