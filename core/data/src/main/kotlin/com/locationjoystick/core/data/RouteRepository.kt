package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.database.dao.RouteDao
import com.locationjoystick.core.database.entities.toDomain
import com.locationjoystick.core.database.entities.toEntity
import com.locationjoystick.core.model.Route
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RouteRepository"

@Singleton
class RouteRepository
    @Inject
    constructor(
        private val routeDao: RouteDao,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        fun getRoutes(): Flow<List<Route>> =
            routeDao.getAllWithWaypoints().map { list ->
                list.map { it.route.toDomain(it.waypoints) }
            }

        fun getRouteWithWaypoints(id: String): Flow<Route?> = routeDao.getWithWaypoints(id).map { it?.route?.toDomain(it.waypoints) }

        suspend fun insertRoute(route: Route): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val waypointEntities = route.waypoints.map { it.toEntity(route.id) }
                    routeDao.insert(route.toEntity())
                    routeDao.replaceWaypoints(route.id, waypointEntities)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to insert route: ${route.id}", e)
                }
            }

        suspend fun updateRoute(route: Route): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val waypointEntities = route.waypoints.map { it.toEntity(route.id) }
                    routeDao.update(route.toEntity())
                    routeDao.replaceWaypoints(route.id, waypointEntities)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to update route: ${route.id}", e)
                }
            }

        suspend fun deleteRoute(id: String): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val entity = routeDao.getById(id)
                    if (entity != null) {
                        routeDao.delete(entity)
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Failed to delete route: $id", e)
                }
            }

        suspend fun removeWaypoint(waypointId: String): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    routeDao.deleteWaypointById(waypointId)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to remove waypoint: $waypointId", e)
                }
            }

        suspend fun renameRoute(
            routeId: String,
            name: String,
        ): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val entity = routeDao.getById(routeId)
                    if (entity != null) {
                        val updated = entity.copy(name = name, updatedAt = System.currentTimeMillis())
                        routeDao.update(updated)
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Failed to rename route: $routeId", e)
                }
            }
    }
