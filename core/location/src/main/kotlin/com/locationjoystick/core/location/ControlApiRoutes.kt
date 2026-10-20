package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.di.ApplicationScope
import com.locationjoystick.core.common.util.advancePosition
import com.locationjoystick.core.common.util.normalizePlantingRadii
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingKind
import com.locationjoystick.core.model.RouteStartConfig
import com.locationjoystick.core.model.canStartRoaming
import com.locationjoystick.core.model.shouldIgnoreJoystickInput
import com.locationjoystick.core.model.shouldPreserveEngineMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the `/api/v1` movement and state commands on [LeaderSyncServer]. Every command calls the
 * same [MapController] method the map screen and widget call, so a leader driven by the API emits
 * the same teleportSeq / active flag / position ticks as one driven from the UI.
 * See docs/features/group-sync.md, "Control API".
 */
@Singleton
class ControlApiRoutes
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val server: LeaderSyncServer,
        private val mapController: MapController,
        private val locationRepository: LocationRepository,
        private val roamingRepository: RoamingRepository,
        private val settingsRepository: SettingsRepository,
        private val routeRepository: RouteRepository,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) {
        @Volatile private var joystickJob: Job? = null

        /** Idempotent: re-registering overwrites the same handlers. */
        fun install() {
            get("state") { state() }
            get("position") {
                val p = locationRepository.currentPosition.value ?: return@get NO_POSITION
                ApiResponse(200, latLngJson(p).toString())
            }
            post("teleport") { json ->
                val target = parseLatLng(json) ?: return@post BAD_COORDS
                // Read before teleporting: teleportTo overwrites the last-teleport time.
                val cooldown = runCatching { runBlocking { mapController.cooldownForPosition(target).first() } }.getOrNull()
                mapController.teleportTo(target)
                val body = JSONObject().put("ok", true)
                if (cooldown is CooldownState.Cooling) {
                    body.put(
                        "warning",
                        JSONObject()
                            .put("code", "teleport_cooldown")
                            .put("message", "Suggested teleport cooldown has not elapsed")
                            .put("remainingSeconds", cooldown.remainingSeconds)
                            .put("totalSeconds", cooldown.totalSeconds)
                            .put("distanceMeters", cooldown.distanceMeters),
                    )
                }
                ApiResponse(200, body.toString())
            }
            post("walk") { json ->
                val target = parseLatLng(json) ?: return@post BAD_COORDS
                if (json.optBoolean("viaRoads", false)) mapController.walkViaRoads(target) else mapController.walkTo(target)
                OK
            }
            command("walk/pause") { mapController.pauseWalk() }
            command("walk/resume") { mapController.resumeWalk() }
            command("walk/stop") { mapController.stopWalk() }
            post("route/start") { json -> routeStart(json) }
            command("route/pause") { mapController.pauseRouteReplay() }
            command("route/resume") { mapController.resumeRouteReplay() }
            command("route/stop") { mapController.stopRouteReplay() }
            post("roam/start") { json -> roamStart(json) }
            command("roam/pause") { mapController.pauseRoaming() }
            command("roam/resume") { mapController.resumeRoaming() }
            command("roam/stop") { mapController.stopRoaming() }
            post("speed-profile") { json ->
                val id = json.optString("id", "")
                if (runBlocking { settingsRepository.getSpeedProfiles().first() }.none { it.id == id }) {
                    return@post apiError(400, "bad_request", "Unknown speed profile")
                }
                runBlocking { settingsRepository.setActiveProfileId(id) }
                OK
            }
            command("spoofing/start") { mapController.startSpoofing() }
            command("spoofing/stop") { mapController.stopSpoofing() }
            post("joystick") { json -> joystick(json) }
        }

        private fun get(
            path: String,
            handler: (ApiRequest) -> ApiResponse,
        ) = server.registerApiRoute("GET", API_PREFIX + path, handler)

        private fun post(
            path: String,
            handler: (JSONObject) -> ApiResponse,
        ) = server.registerApiRoute("POST", API_PREFIX + path) { request ->
            val json =
                try {
                    if (request.body.isNullOrBlank()) JSONObject() else JSONObject(request.body)
                } catch (_: JSONException) {
                    return@registerApiRoute apiError(400, "bad_request", "Malformed JSON body")
                }
            handler(json)
        }

        /** Body-less command. */
        private fun command(
            path: String,
            action: () -> Unit,
        ) = server.registerApiRoute("POST", API_PREFIX + path) {
            action()
            OK
        }

        private fun state(): ApiResponse {
            val walkTarget = locationRepository.walkTarget.value
            val json =
                JSONObject()
                    .put("spoofState", locationRepository.mockLocationState.value.name)
                    .put("mode", locationRepository.currentMode.value.name)
                    .put("position", locationRepository.currentPosition.value?.let(::latLngJson) ?: JSONObject.NULL)
                    .put("bearing", locationRepository.currentBearing.value?.finiteOrNull() ?: JSONObject.NULL)
                    .put("speedMs", locationRepository.currentSpeedMps.value?.finiteOrNull() ?: JSONObject.NULL)
                    .put("activeRouteId", locationRepository.activeRouteId.value ?: JSONObject.NULL)
                    .put(
                        "walk",
                        JSONObject()
                            .put("target", walkTarget?.let(::latLngJson) ?: JSONObject.NULL)
                            .put("paused", locationRepository.isWalkPaused.value),
                    ).put(
                        "roaming",
                        JSONObject()
                            .put("active", roamingRepository.isRoaming.value)
                            .put("paused", roamingRepository.isRoamingPaused.value),
                    ).put("speedProfileId", runBlocking { settingsRepository.getActiveSpeedProfile().first() }.id)
            return ApiResponse(200, json.toString())
        }

        private fun routeStart(json: JSONObject): ApiResponse {
            val routeId = json.optString("routeId", "")
            if (routeId.isEmpty()) return apiError(400, "bad_request", "Missing routeId")
            if (runBlocking { routeRepository.getRouteWithWaypoints(routeId).first() } == null) {
                return apiError(404, "route_not_found", "Route not found")
            }
            val d = RouteStartConfig()
            val config =
                RouteStartConfig(
                    isLooping = json.optBoolean("loop", d.isLooping),
                    isReverse = json.optBoolean("reverse", d.isReverse),
                    isReturnToLocation = json.optBoolean("returnToLocation", d.isReturnToLocation),
                    followRoadsToStart = json.optBoolean("followRoadsToStart", d.followRoadsToStart),
                    isPlanting = json.optBoolean("planting", d.isPlanting),
                    teleportBetweenWaypoints = json.optBoolean("teleportBetweenWaypoints", d.teleportBetweenWaypoints),
                    teleportBetweenDelaySeconds = json.optInt("teleportBetweenDelaySeconds", d.teleportBetweenDelaySeconds),
                )
            // API bypasses Hide Teleport: the user opted into the API's risk.
            mapController.startRouteReplay(routeId, config, bypassHideTeleport = true)
            return OK
        }

        private fun roamStart(json: JSONObject): ApiResponse {
            val kind =
                if (json.has("kind")) {
                    runCatching { RoamingKind.valueOf(json.optString("kind")) }.getOrNull()
                        ?: return apiError(400, "bad_request", "Unknown roaming kind")
                } else {
                    null
                }
            val position =
                if (json.has("lat") || json.has("lon")) {
                    parseLatLng(json) ?: return BAD_COORDS
                } else {
                    locationRepository.currentPosition.value ?: return NO_POSITION
                }
            if (!canStartRoaming(locationRepository.currentMode.value, locationRepository.mockLocationState.value)) {
                return apiError(409, "conflict", "A route is playing; stop it before roaming")
            }
            if (ROAM_NUMBER_KEYS.any { json.has(it) && !json.optDouble(it, Double.NaN).isFinite() }) {
                return apiError(400, "bad_request", "Numeric fields must be finite numbers")
            }
            val base = runBlocking { settingsRepository.getRoamingDefaults().first() }
            val roaming = AppConstants.RoamingConstants
            val (plantingStart, plantingEnd) =
                normalizePlantingRadii(
                    json.optDouble("plantingStartRadiusMeters", base.plantingStartRadiusMeters),
                    json.optDouble("plantingEndRadiusMeters", base.plantingEndRadiusMeters),
                )
            val draft =
                base.copy(
                    radiusMeters =
                        json
                            .optDouble("radiusMeters", base.radiusMeters)
                            .coerceIn(roaming.ROAMING_MIN_RADIUS_METERS, roaming.RADIUS_MAX_METERS),
                    distanceMeters =
                        json
                            .optDouble("distanceMeters", base.distanceMeters)
                            .coerceIn(roaming.DISTANCE_MIN_METERS, roaming.DISTANCE_MAX_METERS),
                    speedProfileId = json.optString("speedProfileId", base.speedProfileId),
                    followRoads = json.optBoolean("followRoads", base.followRoads),
                    returnToInitialLocation = json.optBoolean("returnToInitialLocation", base.returnToInitialLocation),
                    kind = kind ?: base.kind,
                    plantingStartRadiusMeters = plantingStart,
                    plantingEndRadiusMeters = plantingEnd,
                    plantingInfiniteLoops = json.optBoolean("plantingInfiniteLoops", base.plantingInfiniteLoops),
                    plantingLoopCount = json.optInt("plantingLoopCount", base.plantingLoopCount),
                    plantingSpacingMeters =
                        json
                            .optInt("plantingSpacingMeters", base.plantingSpacingMeters)
                            .coerceAtLeast(AppConstants.RoamingConstants.PLANTING_SPACING_MIN_METERS),
                    plantingExpandOnly = json.optBoolean("plantingExpandOnly", base.plantingExpandOnly),
                    plantingSpeedProfileId = json.optString("plantingSpeedProfileId", base.plantingSpeedProfileId),
                )
            val knownProfiles = runBlocking { settingsRepository.getSpeedProfiles().first() }.map { it.id }
            if (draft.speedProfileId !in knownProfiles || draft.plantingSpeedProfileId !in knownProfiles) {
                return apiError(400, "bad_request", "Unknown speed profile")
            }
            mapController.startRoaming(draft, position)
            return OK
        }

        /**
         * One request is a timed stick hold (an HTTP call cannot keep a finger down), capped by
         * [AppConstants.SyncConstants.API_JOYSTICK_MAX_DURATION_MS]. Mirrors JoystickOverlayService:
         * same takeover, same tick cadence, same mode handling and release.
         */
        @Synchronized
        private fun joystick(json: JSONObject): ApiResponse {
            val force = json.optDouble("force", Double.NaN)
            val durationMs = json.optLong("durationMs", -1L)
            if (!json.has("force") || !json.has("durationMs") || force.isNaN()) {
                return apiError(400, "bad_request", "force and durationMs are required")
            }
            if (force <= 0.0 || durationMs <= 0L) {
                joystickJob?.cancel()
                joystickJob = null
                releaseJoystick()
                return OK
            }
            val bearing = json.optDouble("bearingDegrees", Double.NaN)
            if (!bearing.isFinite() || force > 1.0 || durationMs > AppConstants.SyncConstants.API_JOYSTICK_MAX_DURATION_MS) {
                return apiError(400, "bad_request", "Invalid bearingDegrees, force or durationMs")
            }
            if (locationRepository.currentPosition.value == null) return NO_POSITION
            mapController.pauseAutomatedMovement()
            joystickJob?.cancel()
            val ticks = (durationMs / AppConstants.JoystickConstants.STEP_MS).coerceAtLeast(1L)
            joystickJob =
                appScope.launch {
                    val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                    repeat(ticks.toInt()) {
                        delay(AppConstants.JoystickConstants.STEP_MS)
                        joystickTick(bearing, force, speedMs)
                    }
                    releaseJoystick()
                }
            return OK
        }

        private fun joystickTick(
            bearing: Double,
            force: Double,
            speedMs: Double,
        ) {
            val current = locationRepository.currentPosition.value ?: return
            val mode = locationRepository.currentMode.value
            if (shouldIgnoreJoystickInput(
                    mode,
                    locationRepository.mockLocationState.value,
                    roamingRepository.isRoamingPaused.value,
                    locationRepository.isWalkPaused.value,
                )
            ) {
                return
            }
            if (!shouldPreserveEngineMode(mode)) locationRepository.setMockMode(MockMode.JOYSTICK)
            val (lat, lon) =
                advancePosition(
                    current.latitude,
                    current.longitude,
                    bearing,
                    speedMs * AppConstants.JoystickConstants.STEP_SECONDS * force,
                )
            locationRepository.updatePosition(LatLng(lat, lon))
            context.startService(
                MockLocationIntentBuilder.updatePosition(context, lat, lon, (speedMs * force).toFloat(), bearing.toFloat()),
            )
        }

        private fun releaseJoystick() {
            val mode = locationRepository.currentMode.value
            if (!shouldPreserveEngineMode(mode)) locationRepository.setMockMode(MockMode.TELEPORT)
            val engineOwnsMovement =
                shouldIgnoreJoystickInput(
                    mode,
                    locationRepository.mockLocationState.value,
                    roamingRepository.isRoamingPaused.value,
                    locationRepository.isWalkPaused.value,
                )
            if (!engineOwnsMovement) context.startService(MockLocationIntentBuilder.clearMotionVector(context))
        }

        private fun latLngJson(p: LatLng) = JSONObject().put("lat", p.latitude).put("lon", p.longitude)

        private fun Float.finiteOrNull(): Double? = if (isFinite()) toDouble() else null

        private companion object {
            val OK = ApiResponse(200, "{\"ok\":true}")
            val NO_POSITION = apiError(409, "no_position", "No current position")
            val ROAM_NUMBER_KEYS = listOf("radiusMeters", "distanceMeters", "plantingStartRadiusMeters", "plantingEndRadiusMeters")
            val BAD_COORDS = apiError(400, "bad_request", "lat must be -90..90 and lon -180..180")
        }
    }

internal fun parseLatLng(json: JSONObject): LatLng? {
    val lat = json.optDouble("lat", Double.NaN)
    val lon = json.optDouble("lon", Double.NaN)
    return if (lat in -90.0..90.0 && lon in -180.0..180.0) LatLng(lat, lon) else null
}
