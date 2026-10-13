package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.Waypoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers `/api/v1` JSON CRUD for favorites, routes and speed profiles on [LeaderSyncServer].
 * Field names follow the export format (docs/domain-models.md). Speed profiles are the five fixed
 * built-ins: list, get and speed update only. See docs/features/group-sync.md, "Content API".
 */
@Singleton
class ContentApiRoutes
    @Inject
    constructor(
        private val server: LeaderSyncServer,
        private val favoriteRepository: FavoriteRepository,
        private val routeRepository: RouteRepository,
        private val settingsRepository: SettingsRepository,
        private val locationRepository: LocationRepository,
    ) {
        /** Idempotent: re-registering overwrites the same handlers. */
        fun install() {
            handle("GET", "favorites") { _, _ -> favorites() }
            handle("POST", "favorites", body = true) { _, json -> saveFavorite(null, json) }
            handle("GET", "favorites/{id}") { id, _ -> favorite(id!!)?.let { ok(200, favoriteJson(it)) } ?: FAVORITE_404 }
            handle("PUT", "favorites/{id}", body = true) { id, json -> saveFavorite(id!!, json) }
            handle("DELETE", "favorites/{id}") { id, _ ->
                if (favorite(id!!) == null) return@handle FAVORITE_404
                runBlocking { favoriteRepository.deleteFavorite(id) }.fold({ OK }, { INTERNAL })
            }

            handle("GET", "routes") { _, _ ->
                ok(200, JSONArray(runBlocking { routeRepository.getRoutes().first() }.filter { !it.isReserved() }.map(::routeJson)))
            }
            handle("POST", "routes", body = true) { _, json -> saveRoute(null, json) }
            handle("GET", "routes/{id}") { id, _ -> findRoute(id!!)?.let { ok(200, routeJson(it)) } ?: ROUTE_404 }
            handle("PUT", "routes/{id}", body = true) { id, json -> saveRoute(id!!, json) }
            handle("DELETE", "routes/{id}") { id, _ ->
                findRoute(id!!) ?: return@handle ROUTE_404
                if (locationRepository.activeRouteId.value == id) return@handle ROUTE_PLAYING
                runBlocking { routeRepository.deleteRoute(id) }.fold({ OK }, { INTERNAL })
            }

            handle("GET", "speed-profiles") { _, _ -> ok(200, JSONArray(speedProfiles().map(::speedProfileJson))) }
            handle("GET", "speed-profiles/{id}") { id, _ ->
                speedProfiles().firstOrNull { it.id == id }?.let { ok(200, speedProfileJson(it)) }
                    ?: PROFILE_404
            }
            handle("PUT", "speed-profiles/{id}", body = true) { id, json -> setSpeed(id!!, json) }
        }

        /** Registers [method] on `/api/v1/[path]`; [body] routes get the parsed JSON object (400 if malformed). */
        private fun handle(
            method: String,
            path: String,
            body: Boolean = false,
            handler: (String?, JSONObject) -> ApiResponse,
        ) = server.registerApiRoute(method, API_PREFIX + path) { request ->
            val json =
                try {
                    if (!body || request.body.isNullOrBlank()) JSONObject() else JSONObject(request.body)
                } catch (_: JSONException) {
                    return@registerApiRoute apiError(400, "bad_request", "Malformed JSON body")
                }
            try {
                handler(request.pathParam, json)
            } catch (e: IllegalArgumentException) {
                apiError(400, "bad_request", e.message ?: "Invalid input")
            }
        }

        // ---- favorites ----

        private fun favorite(id: String) = runBlocking { favoriteRepository.getFavorites().first() }.firstOrNull { it.id == id }

        private fun favorites() = ok(200, JSONArray(runBlocking { favoriteRepository.getFavorites().first() }.map(::favoriteJson)))

        private fun saveFavorite(
            id: String?,
            json: JSONObject,
        ): ApiResponse {
            val existing = id?.let { favorite(it) ?: return FAVORITE_404 }
            val name = json.optString("name", "").trim()
            require(name.isNotEmpty()) { "name is required" }
            val position = parseLatLng(json) ?: throw IllegalArgumentException(BAD_COORDS_MESSAGE)
            val category = if (json.isNull("category")) null else json.optString("category").trim().ifEmpty { null }
            val saved =
                FavoriteLocation(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = name,
                    position = position,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    category = category,
                )
            val result =
                runBlocking {
                    if (existing == null) {
                        favoriteRepository.addFavorite(saved.id, name, position, saved.createdAt, category)
                    } else {
                        favoriteRepository.updateFavorite(saved)
                    }
                }
            return result.fold({ ok(if (existing == null) 201 else 200, favoriteJson(saved)) }, { INTERNAL })
        }

        private fun favoriteJson(f: FavoriteLocation) =
            JSONObject()
                .put("id", f.id)
                .put("name", f.name)
                .put("lat", f.position.latitude)
                .put("lon", f.position.longitude)
                .put("createdAt", f.createdAt)
                .put("category", f.category ?: JSONObject.NULL)

        // ---- routes ----

        private fun Route.isReserved() = id == AppConstants.RouteConstants.PASTE_TEMP_ROUTE_ID

        private fun findRoute(id: String) =
            runBlocking { routeRepository.getRouteWithWaypoints(id).first() }?.takeUnless { it.isReserved() }

        private fun saveRoute(
            id: String?,
            json: JSONObject,
        ): ApiResponse {
            val existing = id?.let { findRoute(it) ?: return ROUTE_404 }
            if (existing != null && locationRepository.activeRouteId.value == existing.id) return ROUTE_PLAYING
            val name = json.optString("name", "").trim()
            require(name.isNotEmpty()) { "name is required" }
            val type =
                if (json.has("routeType")) {
                    runCatching { RouteType.valueOf(json.optString("routeType")) }.getOrNull()
                        ?: throw IllegalArgumentException("Unknown routeType")
                } else {
                    RouteType.STRAIGHT
                }
            val profileId = if (json.isNull("speedProfileId")) null else json.optString("speedProfileId")
            require(profileId == null || SpeedProfile.defaultProfiles().any { it.id == profileId }) { "Unknown speedProfileId" }
            val points = json.optJSONArray("waypoints") ?: JSONArray()
            require(points.length() >= 2) { "At least 2 waypoints are required" }
            val waypoints =
                List(points.length()) { i ->
                    val w = points.optJSONObject(i) ?: throw IllegalArgumentException("Invalid waypoint")
                    val wait = w.optInt("waitSeconds", 0)
                    require(wait >= 0) { "waitSeconds must be >= 0" }
                    Waypoint(
                        id = UUID.randomUUID().toString(),
                        position = parseLatLng(w) ?: throw IllegalArgumentException(BAD_COORDS_MESSAGE),
                        orderIndex = i,
                        waitSeconds = wait,
                    )
                }
            val plantingRadius =
                when {
                    !json.has("plantingRadiusMeters") -> existing?.plantingRadiusMeters
                    json.isNull("plantingRadiusMeters") -> null
                    else ->
                        json.optDouble("plantingRadiusMeters").also {
                            val min = AppConstants.RouteConstants.ROUTE_MIN_RADIUS_METERS
                            val max = AppConstants.RouteConstants.PLANTING_MAX_RADIUS_METERS
                            require(it in min..max) { "plantingRadiusMeters out of range" }
                        }
                }
            val now = System.currentTimeMillis()
            val saved =
                Route(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = name,
                    waypoints = waypoints,
                    isLooping = json.optBoolean("isLooping", false),
                    routeType = type,
                    speedProfileId = profileId,
                    randomizeTeleportOrder = json.optBoolean("randomizeTeleportOrder", false),
                    plantingRadiusMeters = plantingRadius,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    category = existing?.category,
                )
            val result = runBlocking { if (existing == null) routeRepository.insertRoute(saved) else routeRepository.updateRoute(saved) }
            return result.fold({ ok(if (existing == null) 201 else 200, routeJson(saved)) }, { INTERNAL })
        }

        private fun routeJson(r: Route) =
            JSONObject()
                .apply { r.plantingRadiusMeters?.let { put("plantingRadiusMeters", it) } }
                .put("id", r.id)
                .put("name", r.name)
                .put("isLooping", r.isLooping)
                .put("routeType", r.routeType.name)
                .put("speedProfileId", r.speedProfileId ?: JSONObject.NULL)
                .put("randomizeTeleportOrder", r.randomizeTeleportOrder)
                .put("createdAt", r.createdAt)
                .put("updatedAt", r.updatedAt)
                .put(
                    "waypoints",
                    JSONArray(
                        r.waypoints.map {
                            JSONObject()
                                .put("id", it.id)
                                .put("lat", it.position.latitude)
                                .put("lon", it.position.longitude)
                                .put("orderIndex", it.orderIndex)
                                .put("waitSeconds", it.waitSeconds)
                        },
                    ),
                )

        // ---- speed profiles ----

        private fun speedProfiles() = runBlocking { settingsRepository.getSpeedProfiles().first() }

        private fun speedProfileJson(p: SpeedProfile) =
            JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("speedMetersPerSecond", p.speedMetersPerSecond)
                .put("builtIn", true)
                .put("active", runBlocking { settingsRepository.getActiveSpeedProfile().first() }.id == p.id)
                .put("enabled", p.id in runBlocking { settingsRepository.getEnabledSpeedProfileIds().first() })

        private fun setSpeed(
            id: String,
            json: JSONObject,
        ): ApiResponse {
            if (speedProfiles().none { it.id == id }) return PROFILE_404
            val speed = json.optDouble("speedMetersPerSecond", Double.NaN)
            require(speed in AppConstants.ProfileConstants.MIN_SPEED_MS..AppConstants.ProfileConstants.MAX_SPEED_MS) {
                "speedMetersPerSecond must be 0.01..15.0"
            }
            runBlocking {
                when (id) {
                    "slow_walk" -> settingsRepository.setSlowWalkSpeed(speed)
                    "walk" -> settingsRepository.setWalkSpeed(speed)
                    "run" -> settingsRepository.setRunSpeed(speed)
                    "bike" -> settingsRepository.setBikeSpeed(speed)
                    else -> settingsRepository.setDriveSpeed(speed)
                }
            }
            return speedProfiles().firstOrNull { it.id == id }?.let { ok(200, speedProfileJson(it)) } ?: PROFILE_404
        }

        private fun ok(
            status: Int,
            body: Any,
        ) = ApiResponse(status, body.toString())

        private companion object {
            const val BAD_COORDS_MESSAGE = "lat must be -90..90 and lon -180..180"
            val OK = ApiResponse(200, "{\"ok\":true}")
            val INTERNAL = apiError(500, "internal_error", "Internal error")
            val FAVORITE_404 = apiError(404, "favorite_not_found", "Favorite not found")
            val ROUTE_404 = apiError(404, "route_not_found", "Route not found")
            val PROFILE_404 = apiError(404, "speed_profile_not_found", "Speed profile not found")
            val ROUTE_PLAYING = apiError(409, "conflict", "Route is playing; stop it first")
        }
    }
