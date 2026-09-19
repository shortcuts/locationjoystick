package com.locationjoystick.core.data

import android.content.Context
import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.database.dao.RouteDao
import com.locationjoystick.core.database.entities.RouteEntity
import com.locationjoystick.core.database.entities.WaypointEntity
import com.locationjoystick.core.database.entities.toDomain
import com.locationjoystick.core.database.entities.toEntity
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RouteRepository"

data class HotRoute(
    val name: String,
    val country: String,
    val city: String,
    val assetPath: String,
    val routeType: RouteType = RouteType.STRAIGHT,
)

@Singleton
class RouteRepository
    @Inject
    constructor(
        private val routeDao: RouteDao,
        @param:ApplicationContext private val context: Context,
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
            selectedIds: Set<String> =
                HOT_ROUTES
                    .map {
                        idForRoute(it.name, it.city)
                    }.toSet(),
        ): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val now = System.currentTimeMillis()
                    val toInsert = mutableListOf<Pair<RouteEntity, List<WaypointEntity>>>()
                    val toUpdate = mutableListOf<Pair<RouteEntity, List<WaypointEntity>>>()
                    val toDelete = mutableListOf<RouteEntity>()

                    // Phase 1: read assets + current DB state; build batch lists (no writes yet).
                    for (hotRoute in HOT_ROUTES) {
                        val id = idForRoute(hotRoute.name, hotRoute.city)
                        val existing = routeDao.getById(id)
                        if (id in selectedIds) {
                            val gpxContent =
                                context.assets
                                    .open(hotRoute.assetPath)
                                    .bufferedReader()
                                    .readText()
                            val waypoints =
                                parseWptGpx(gpxContent).mapIndexed { index, latLng ->
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
                                toUpdate.add(
                                    entity to waypoints,
                                )
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

            fun parseWptGpx(content: String): List<LatLng> {
                val result = mutableListOf<LatLng>()
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(content.reader())
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "wpt") {
                        val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        if (lat != null && lon != null) result.add(LatLng(lat, lon))
                    }
                    event = parser.next()
                }
                return result
            }

            val HOT_ROUTES =
                listOf(
                    HotRoute("Faelledparken", "Denmark", "Copenhagen", "hot_routes/cph_park.gpx"),
                    HotRoute(
                        "Faelledparken (Via Roads)",
                        "Denmark",
                        "Copenhagen",
                        "hot_routes/cph_park.gpx",
                        RouteType.GUIDED,
                    ),
                    HotRoute(
                        "Go Stamp Rally: Minato",
                        "Japan",
                        "Tokyo",
                        "hot_routes/tokyo_minato_stamp_rally.gpx",
                    ),
                    HotRoute(
                        "Go Stamp Rally: Koto",
                        "Japan",
                        "Tokyo",
                        "hot_routes/tokyo_koto_stamp_rally.gpx",
                    ),
                    HotRoute(
                        "Go Stamp Rally: Shinagawa",
                        "Japan",
                        "Tokyo",
                        "hot_routes/tokyo_shinagawa_stamp_rally.gpx",
                    ),
                )
        }
    }
