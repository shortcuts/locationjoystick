package com.locationjoystick.core.routing

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private fun Double.toRadians(): Double = Math.toRadians(this)

private const val TAG = "RoamingEngine"

/**
 * Engine for simulating random walking within a circular area.
 *
 * Supports two modes:
 * - Straight-line walking (no network required)
 * - Road-following via OSRM routing (requires internet, falls back to straight-line on failure)
 *
 * Usage:
 * ```
 * val config = RoamingConfig(centerPosition = LatLng(48.8566, 2.3522), radiusMeters = 500.0, ...)
 * roamingEngine.startRoaming(config, speedMs = 1.5) { position ->
 *     // Called each tick with new position
 * }
 * ```
 *
 * The engine runs on [engineScope] and can be stopped via [stopRoaming].
 * Only one roaming session can be active at a time — starting a new one cancels any existing.
 */
@Singleton
class RoamingEngine
    @Inject
    constructor(
        private val osrmClient: OsrmClient,
        private val routeInterpolator: RouteInterpolator,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    ) : AutoCloseable {
        /** Coroutine scope for all roaming coroutines. Uses SupervisorJob so one failure doesn't cancel others. */
        private val engineScope = CoroutineScope(SupervisorJob() + dispatcher)

        /** Current active roaming job. Null when not roaming. */
        @Volatile private var activeJob: Job? = null

        /** When true, the roaming loop suspends position updates until resumed. */
        @Volatile private var isPaused = false

        fun pauseRoaming() {
            isPaused = true
        }

        fun resumeRoaming() {
            isPaused = false
        }

        /**
         * Generates a uniformly random point within a circle of given radius.
         * Uses polar coordinate transformation with square-root for uniform distribution.
         *
         * @param center Center of the circle
         * @param radiusMeters Radius in meters
         * @return Random LatLng within the circle
         */
        fun randomPointInRadius(
            center: LatLng,
            radiusMeters: Double,
        ): LatLng {
            val r = radiusMeters * sqrt(Random.nextDouble())
            val theta = Random.nextDouble() * 2 * PI
            val dLat = r * cos(theta) / AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
            val dLon = r * sin(theta) / (AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE * cos(center.latitude.toRadians()))
            return LatLng(center.latitude + dLat, center.longitude + dLon)
        }

        /**
         * Starts a roaming session.
         *
         * Any previously active session is cancelled and awaited (via [cancelAndJoin]) inside the
         * new coroutine before movement begins, so two sessions never overlap.
         *
         * @param config Roaming configuration (center, radius, duration, etc.)
         * @param speedMs Movement speed in meters per second
         * @param onRouteUpdate Callback invoked when route changes (for UI display)
         * @param onComplete Callback invoked when the session ends naturally (not on cancellation)
         * @param onPositionUpdate Callback invoked each tick with new position
         * @return Job that can be used to track/cancel the roaming session
         */
        fun startRoaming(
            config: RoamingConfig,
            speedMs: Double,
            onRouteUpdate: (List<LatLng>) -> Unit = {},
            onComplete: () -> Unit = {},
            onPositionUpdate: (LatLng) -> Unit,
        ): Job {
            // Capture previous job before launching. The new coroutine awaits its cancellation
            // via cancelAndJoin so the two sessions never overlap.
            val previous = activeJob
            activeJob = null

            val job =
                engineScope.launch {
                    previous?.cancelAndJoin()
                    isPaused = false
                    val initialLocation = config.centerPosition
                    var currentPosition = config.centerPosition
                    var remainingMeters = config.distanceMeters
                    val distancePerTick = speedMs * (AppConstants.LocationConstants.UPDATE_INTERVAL_MS / 1000.0)

                    val firstLegPreview = config.previewWaypoints?.takeIf { it.size >= 2 }
                    var isFirstLeg = true
                    while (isActive && remainingMeters > 0) {
                        val route =
                            if (isFirstLeg && firstLegPreview != null) {
                                isFirstLeg = false
                                firstLegPreview
                            } else {
                                isFirstLeg = false
                                val destination = randomPointInRadius(config.centerPosition, config.radiusMeters)
                                fetchRoute(config, currentPosition, destination)
                            }
                        onRouteUpdate(route)
                        // Guard against empty route to avoid a tight busy-spin
                        if (route.size < 2) {
                            delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                            continue
                        }
                        var ticks = 0
                        currentPosition =
                            walkRouteSegment(route, currentPosition, speedMs, onPositionUpdate) {
                                ticks++
                            }
                        remainingMeters -= ticks * distancePerTick
                        onRouteUpdate(emptyList())
                    }

                    if (config.returnToInitialLocation && isActive) {
                        val returnRoute = fetchRoute(config, currentPosition, initialLocation)
                        onRouteUpdate(returnRoute)
                        if (returnRoute.size >= 2) {
                            currentPosition =
                                walkRouteSegment(returnRoute, currentPosition, speedMs, onPositionUpdate)
                        }
                    }

                    onComplete()
                }
            activeJob = job
            return job
        }

        /**
         * Walks [route] from [startPosition] to completion or coroutine cancellation.
         *
         * On each tick: waits while paused, calls [routeInterpolator], invokes [onPositionUpdate],
         * calls [onTick], delays one interval, and breaks when the end is reached.
         *
         * @return Final position reached (last position emitted to [onPositionUpdate]).
         */
        private suspend fun walkRouteSegment(
            route: List<LatLng>,
            startPosition: LatLng,
            speedMs: Double,
            onPositionUpdate: (LatLng) -> Unit,
            onTick: () -> Unit = {},
        ): LatLng {
            var currentPosition = startPosition
            var waypointIndex = 0
            while (currentCoroutineContext().isActive) {
                while (isPaused && currentCoroutineContext().isActive) {
                    delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                }
                if (!currentCoroutineContext().isActive) break
                val result =
                    routeInterpolator.interpolateAlongRoute(
                        waypoints = route,
                        currentPosition = currentPosition,
                        currentWaypointIndex = waypointIndex,
                        speedMs = speedMs,
                        deltaTimeMs = AppConstants.LocationConstants.UPDATE_INTERVAL_MS,
                    )
                currentPosition = result.position
                waypointIndex = result.nextWaypointIndex
                onPositionUpdate(currentPosition)
                onTick()
                if (result.reachedEnd) break
                delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
            }
            return currentPosition
        }

        /**
         * Stops the current roaming session gracefully.
         * Waits for the active coroutine to finish before returning.
         */
        suspend fun stopRoaming() {
            isPaused = false
            val job = activeJob
            activeJob = null
            job?.cancelAndJoin()
        }

        /**
         * Stops the current roaming session without tearing down the engine scope.
         * The engine can be restarted via [startRoaming] after this call.
         */
        fun stop() {
            val job = activeJob
            activeJob = null
            job?.cancel()
        }

        /**
         * Implements [AutoCloseable] to fully release the engine scope.
         * Call only when the engine will never be reused (e.g. process teardown).
         */
        override fun close() {
            activeJob?.cancel()
            engineScope.cancel()
        }

        /**
         * Fetches a preview route between two points for UI display purposes.
         * Does not start any roaming session.
         *
         * @param from Starting position
         * @param to Destination position
         * @param useRoadSnapping If true, uses OSRM for road-following; falls back to straight-line on failure
         * @param speedProfileId Used to select the OSRM profile (e.g. "bike" → cycling, else foot)
         * @return List of waypoints forming the route
         */
        suspend fun previewRoute(
            from: LatLng,
            to: LatLng,
            useRoadSnapping: Boolean,
            speedProfileId: String,
        ): List<LatLng> {
            if (!useRoadSnapping) {
                return osrmClient.straightLineRoute(from, to)
            }
            val profile =
                when (speedProfileId) {
                    "bike" -> AppConstants.RoamingConstants.OSRM_PROFILE_CYCLING
                    else -> AppConstants.RoamingConstants.OSRM_PROFILE_FOOT
                }
            return try {
                osrmClient
                    .getRoute(profile, listOf(from, to))
                    .getOrElse { e ->
                        Log.w(TAG, "OSRM unavailable for preview, using straight-line fallback", e)
                        osrmClient.straightLineRoute(from, to)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Preview route fetch failed, using straight-line fallback", e)
                osrmClient.straightLineRoute(from, to)
            }
        }

        /**
         * Fetches a route between two points.
         * If [config.useRoadSnapping] is true, uses OSRM for road-following.
         * Falls back to straight-line on OSRM failure.
         *
         * @param config Roaming configuration
         * @param from Starting position
         * @param to Destination position
         * @return List of waypoints forming the route
         */
        private suspend fun fetchRoute(
            config: RoamingConfig,
            from: LatLng,
            to: LatLng,
        ): List<LatLng> {
            if (!config.useRoadSnapping) {
                return osrmClient.straightLineRoute(from, to)
            }
            val profile =
                when (config.speedProfileId) {
                    "bike" -> AppConstants.RoamingConstants.OSRM_PROFILE_CYCLING
                    else -> AppConstants.RoamingConstants.OSRM_PROFILE_FOOT
                }
            return osrmClient
                .getRoute(profile, listOf(from, to))
                .getOrElse { e ->
                    Log.w(TAG, "OSRM unavailable, using straight-line fallback", e)
                    osrmClient.straightLineRoute(from, to)
                }
        }
    }
