package com.locationjoystick.core.data

import android.content.Context
import android.util.Log
import com.locationjoystick.core.database.dao.RouteDao
import com.locationjoystick.core.database.entities.RouteEntity
import com.locationjoystick.core.database.entities.WaypointEntity
import com.locationjoystick.core.database.entities.toDomain
import com.locationjoystick.core.database.entities.toEntity
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RouteRepository"

data class HotRoute(
    val name: String,
    val country: String,
    val city: String,
    val assetPath: String,
)

@Singleton
class RouteRepository
    @Inject
    constructor(
        private val routeDao: RouteDao,
        @ApplicationContext private val context: Context,
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

        suspend fun deleteAllRoutes(): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    routeDao.deleteAllWaypoints()
                    routeDao.deleteAll()
                }.onFailure { e ->
                    Log.e(TAG, "Failed to delete all routes", e)
                }
            }

        suspend fun upsertHotRoutes(selectedIds: Set<String> = HOT_ROUTES.map { idForRoute(it.name, it.city) }.toSet()): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    HOT_ROUTES.forEach { hotRoute ->
                        val id = idForRoute(hotRoute.name, hotRoute.city)
                        if (id in selectedIds) {
                            val gpxContent =
                                context.assets
                                    .open(hotRoute.assetPath)
                                    .bufferedReader()
                                    .readText()
                            val latLngs = parseWptGpx(gpxContent)
                            val now = System.currentTimeMillis()
                            val waypoints =
                                latLngs.mapIndexed { index, latLng ->
                                    WaypointEntity(
                                        id = "$id:$index",
                                        routeId = id,
                                        latitude = latLng.latitude,
                                        longitude = latLng.longitude,
                                        orderIndex = index,
                                    )
                                }
                            val existing = routeDao.getById(id)
                            if (existing != null) {
                                routeDao.update(existing.copy(name = hotRoute.name, updatedAt = now))
                                routeDao.replaceWaypoints(id, waypoints)
                            } else {
                                routeDao.insert(
                                    RouteEntity(
                                        id = id,
                                        name = hotRoute.name,
                                        isLooping = false,
                                        routeType = "STRAIGHT",
                                        createdAt = now,
                                        updatedAt = now,
                                    ),
                                )
                                routeDao.insertWaypoints(waypoints)
                            }
                        } else {
                            val existing = routeDao.getById(id)
                            if (existing != null) routeDao.delete(existing)
                        }
                    }
                }.onFailure { e -> Log.e(TAG, "Failed to upsert hot routes", e) }
            }

        suspend fun removeHotRoutes(): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    routeDao.deleteHotRoutes()
                }.onFailure { e -> Log.e(TAG, "Failed to remove hot routes", e) }
            }

        companion object {
            private const val HOT_ROUTE_ID_PREFIX = "hot_route_"

            fun idForRoute(
                name: String,
                city: String,
            ): String = HOT_ROUTE_ID_PREFIX + "$name $city".lowercase().replace(Regex("[^a-z0-9]"), "_")

            fun parseWptGpx(content: String): List<LatLng> {
                val regex = Regex("""<wpt\s+lat="([^"]+)"\s+lon="([^"]+)"""")
                return regex
                    .findAll(content)
                    .mapNotNull { match ->
                        val lat = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                        val lon = match.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                        LatLng(lat, lon)
                    }.toList()
            }

            val HOT_ROUTES =
                listOf(
                    HotRoute("City - Copenhagen", "Denmark", "Copenhagen", "hot_routes/cph_city.gpx"),
                    HotRoute("Park - Faelledparken", "Denmark", "Copenhagen", "hot_routes/cph_park.gpx"),
                    HotRoute("Stamp Rally - LEGO", "Denmark", "Copenhagen", "hot_routes/cph_stamp_rally.gpx"),
                )
        }
    }
