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
    ) : AutoCloseable {
        /** Coroutine scope for all roaming coroutines. Uses SupervisorJob so one failure doesn't cancel others. */
        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Current active roaming job. Null when not roaming. */
        private var activeJob: Job? = null

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
         * @param config Roaming configuration (center, radius, duration, etc.)
         * @param speedMs Movement speed in meters per second
         * @param onPositionUpdate Callback invoked each tick with new position
         * @param onRouteUpdate Callback invoked when route changes (for UI display)
         * @return Job that can be used to track/cancel the roaming session
         */
        fun startRoaming(
            config: RoamingConfig,
            speedMs: Double,
            onPositionUpdate: (LatLng) -> Unit,
            onRouteUpdate: (List<LatLng>) -> Unit = {},
        ): Job {
            activeJob?.cancel()
            val job =
                engineScope.launch {
                    val initialLocation = config.centerPosition
                    var currentPosition = config.centerPosition
                    var remainingMeters = config.distanceMeters
                    var currentRoute: List<LatLng> = emptyList()
                    var waypointIndex = 0

                    val distancePerTick = speedMs * (AppConstants.LocationConstants.UPDATE_INTERVAL_MS / 1000.0)
                    while (isActive && remainingMeters > 0) {
                        if (currentRoute.isEmpty() || waypointIndex >= currentRoute.size) {
                            val destination = randomPointInRadius(config.centerPosition, config.radiusMeters)
                            currentRoute = fetchRoute(config, currentPosition, destination)
                            onRouteUpdate(currentRoute)
                            waypointIndex = 0
                            // Guard against empty route to avoid a tight busy-spin
                            if (currentRoute.size < 2) {
                                delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                                continue
                            }
                        }

                        val result =
                            routeInterpolator.interpolateAlongRoute(
                                waypoints = currentRoute,
                                currentPosition = currentPosition,
                                currentWaypointIndex = waypointIndex,
                                speedMs = speedMs,
                                deltaTimeMs = AppConstants.LocationConstants.UPDATE_INTERVAL_MS,
                            )

                        currentPosition = result.position
                        waypointIndex = result.nextWaypointIndex
                        remainingMeters -= distancePerTick

                        if (result.reachedEnd) {
                            currentRoute = emptyList()
                            onRouteUpdate(emptyList())
                        }

                        onPositionUpdate(currentPosition)
                        delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                    }

                    if (config.returnToInitialLocation && isActive) {
                        val returnRoute = fetchRoute(config, currentPosition, initialLocation)
                        onRouteUpdate(returnRoute)
                        var returnIndex = 0
                        while (isActive && returnRoute.isNotEmpty()) {
                            val result =
                                routeInterpolator.interpolateAlongRoute(
                                    waypoints = returnRoute,
                                    currentPosition = currentPosition,
                                    currentWaypointIndex = returnIndex,
                                    speedMs = speedMs,
                                    deltaTimeMs = AppConstants.LocationConstants.UPDATE_INTERVAL_MS,
                                )
                            currentPosition = result.position
                            returnIndex = result.nextWaypointIndex
                            onPositionUpdate(currentPosition)
                            if (result.reachedEnd) break
                            delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                        }
                    }
                }
            activeJob = job
            return job
        }

        /**
         * Stops the current roaming session gracefully.
         * Waits for the active coroutine to finish before returning.
         */
        suspend fun stopRoaming() {
            activeJob?.cancelAndJoin()
            activeJob = null
        }

        /**
         * Stops the current roaming session without tearing down the engine scope.
         * The engine can be restarted via [startRoaming] after this call.
         */
        fun stop() {
            activeJob?.cancel()
            activeJob = null
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
