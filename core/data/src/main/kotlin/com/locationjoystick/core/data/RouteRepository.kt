package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.clampPlantingRadius
import com.locationjoystick.core.database.dao.RouteDao
import com.locationjoystick.core.database.entities.RouteEntity
import com.locationjoystick.core.database.entities.WaypointEntity
import com.locationjoystick.core.database.entities.toDomain
import com.locationjoystick.core.database.entities.toEntity
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
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

        fun getRouteWithWaypoints(id: String): Flow<Route?> =
            routeDao.getWithWaypoints(id).map {
                it?.route?.toDomain(it.waypoints)
            }

        suspend fun insertRoute(route: Route): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    writeRoute(route)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to insert route: ${route.id}", e)
                }
            }

        /**
         * Overwrites the single reserved paste-play route ([AppConstants.RouteConstants.PASTE_TEMP_ROUTE_ID]).
         * Later Start pastes replace this id only. A UUID save, even one named
         * [AppConstants.RouteConstants.PASTE_TEMP_ROUTE_NAME], is never matched or overwritten.
         */
        suspend fun upsertPasteTempRoute(points: List<LatLng>): Result<Route> =
            withContext(ioDispatcher) {
                runCatching {
                    require(points.size >= 2) { "Need at least 2 points to start a pasted route" }
                    val existing = routeDao.getById(AppConstants.RouteConstants.PASTE_TEMP_ROUTE_ID)
                    val now = System.currentTimeMillis()
                    val route =
                        routeFromPoints(
                            id = AppConstants.RouteConstants.PASTE_TEMP_ROUTE_ID,
                            name = AppConstants.RouteConstants.PASTE_TEMP_ROUTE_NAME,
                            points = points,
                            nowMs = now,
                            createdAt = existing?.createdAt ?: now,
                        )
                    writeRoute(route)
                    route
                }.onFailure { e ->
                    Log.e(TAG, "Failed to upsert paste temp route", e)
                }
            }

        /**
         * Inserts a new UUID route from pasted points. Never writes
         * [AppConstants.RouteConstants.PASTE_TEMP_ROUTE_ID]. Waypoints are cloned with new ids.
         */
        suspend fun insertNamedPastedRoute(
            name: String,
            points: List<LatLng>,
        ): Result<Route> =
            withContext(ioDispatcher) {
                runCatching {
                    val trimmed = name.trim()
                    require(trimmed.isNotEmpty()) { "Route name is required" }
                    require(points.size >= 2) { "Need at least 2 points to save a pasted route" }
                    val now = System.currentTimeMillis()
                    val route =
                        routeFromPoints(
                            id = UUID.randomUUID().toString(),
                            name = trimmed,
                            points = points,
                            nowMs = now,
                            createdAt = now,
                        )
                    writeRoute(route)
                    route
                }.onFailure { e ->
                    Log.e(TAG, "Failed to insert named pasted route", e)
                }
            }

        private suspend fun writeRoute(route: Route) {
            val waypointEntities = route.waypoints.map { it.toEntity(route.id) }
            routeDao.insert(route.toEntity())
            routeDao.replaceWaypoints(route.id, waypointEntities)
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

        suspend fun setWaypointWaitSeconds(
            waypointId: String,
            waitSeconds: Int,
        ): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    routeDao.updateWaitSeconds(waypointId, waitSeconds)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to set waypoint wait seconds: $waypointId", e)
                }
            }

        suspend fun setAllWaypointsWaitSeconds(
            routeId: String,
            waitSeconds: Int,
        ): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    routeDao.updateWaitSecondsForRoute(routeId, waitSeconds)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to set all waypoints wait seconds: $routeId", e)
                }
            }

        suspend fun setRandomizeTeleportOrder(
            routeId: String,
            randomize: Boolean,
        ): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val entity = routeDao.getById(routeId)
                    if (entity != null) {
                        routeDao.update(entity.copy(randomizeTeleportOrder = randomize, updatedAt = System.currentTimeMillis()))
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Failed to set randomize teleport order: $routeId", e)
                }
            }

        suspend fun setPlantingRadius(
            routeId: String,
            radiusMeters: Double?,
        ): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val entity = routeDao.getById(routeId)
                    if (entity != null) {
                        routeDao.update(
                            entity.copy(
                                plantingRadiusMeters = radiusMeters?.let(::clampPlantingRadius),
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Failed to set planting radius: $routeId", e)
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

        /** Moves [routeId] into user group [category] (null = ungrouped). Hot routes stay ungrouped. */
        suspend fun setCategory(
            routeId: String,
            category: String?,
        ): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val entity = routeDao.getById(routeId)
                    if (entity != null && !entity.id.startsWith(HOT_ROUTE_ID_PREFIX)) {
                        routeDao.update(entity.copy(category = category))
                    }
                }.onFailure { e -> Log.e(TAG, "Failed to set route category: $routeId", e) }
            }

        /** Renames user group [from] to [to], or ungroups its members when [to] is null. */
        suspend fun renameCategory(
            from: String,
            to: String?,
        ): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    routeDao
                        .getAll()
                        .first()
                        .filter { it.category == from && !it.id.startsWith(HOT_ROUTE_ID_PREFIX) }
                        .forEach { routeDao.update(it.copy(category = to)) }
                }.onFailure { e -> Log.e(TAG, "Failed to rename route group: $from", e) }
            }

        suspend fun setRouteSpeedProfile(
            routeId: String,
            speedProfileId: String?,
        ): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val entity = routeDao.getById(routeId)
                    if (entity != null) {
                        val updated =
                            entity.copy(
                                speedProfileId = speedProfileId,
                                updatedAt = System.currentTimeMillis(),
                            )
                        routeDao.update(updated)
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Failed to set route speed profile: $routeId", e)
                }
            }

        suspend fun deleteAllRoutes(): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    routeDao.deleteAllWaypoints()
                    routeDao.deleteAll()
                }.onFailure { e ->
                    Log.e(TAG, "Failed to delete all routes", e)
                }
            }

        suspend fun upsertHotRoutes(
            routes: List<HotRoute>,
            selectedIds: Set<String>,
        ): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val now = System.currentTimeMillis()
                    val toInsert = mutableListOf<Pair<RouteEntity, List<WaypointEntity>>>()
                    val toUpdate = mutableListOf<Pair<RouteEntity, List<WaypointEntity>>>()
                    val toDelete = mutableListOf<RouteEntity>()

                    // Phase 1: read current DB state; build batch lists (no writes yet).
                    val listedIds = routes.map { idForRoute(it.name, it.city) }.toSet()
                    routeDao
                        .getAll()
                        .first()
                        .filter { it.id.startsWith(HOT_ROUTE_ID_PREFIX) && it.id !in listedIds }
                        .let(toDelete::addAll)
                    for (hotRoute in routes) {
                        val id = idForRoute(hotRoute.name, hotRoute.city)
                        val existing = routeDao.getById(id)
                        if (id in selectedIds) {
                            val waypoints =
                                hotRoute.waypoints.mapIndexed { index, latLng ->
                                    WaypointEntity(
                                        id = "$id:$index",
                                        routeId = id,
                                        latitude = latLng.latitude,
                                        longitude = latLng.longitude,
                                        orderIndex = index,
                                    )
                                }
                            val entity =
                                RouteEntity(
                                    id = id,
                                    name = hotRoute.name,
                                    isLooping = false,
                                    routeType = hotRoute.routeType.name,
                                    createdAt = existing?.createdAt ?: now,
                                    updatedAt = now,
                                )
                            if (existing != null) {
                                toUpdate.add(entity to waypoints)
                            } else {
                                toInsert.add(entity to waypoints)
                            }
                        } else if (existing != null) {
                            toDelete.add(existing)
                        }
                    }

                    // Phase 2: apply all writes atomically.
                    routeDao.applyHotRouteBatch(toInsert, toUpdate, toDelete)
                }.onFailure { e -> Log.e(TAG, "Failed to upsert hot routes", e) }
            }

        suspend fun removeHotRoutes(): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    routeDao.deleteHotRoutes()
                }.onFailure { e -> Log.e(TAG, "Failed to remove hot routes", e) }
            }

        private fun routeFromPoints(
            id: String,
            name: String,
            points: List<LatLng>,
            nowMs: Long,
            createdAt: Long,
        ): Route {
            val waypoints =
                points.mapIndexed { index, latLng ->
                    Waypoint(
                        id = UUID.randomUUID().toString(),
                        position = latLng,
                        orderIndex = index,
                    )
                }
            return Route(
                id = id,
                name = name,
                waypoints = waypoints,
                isLooping = false,
                routeType = RouteType.STRAIGHT,
                createdAt = createdAt,
                updatedAt = nowMs,
            )
        }

        companion object {
            private const val HOT_ROUTE_ID_PREFIX = "hot_route_"

            fun idForRoute(
                name: String,
                city: String,
            ): String = HOT_ROUTE_ID_PREFIX + "$name $city".lowercase().replace(Regex("[^a-z0-9]"), "_")
        }
    }
