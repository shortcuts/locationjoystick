package com.locationjoystick.core.routing

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.haversineDistance
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
import kotlin.math.max
import kotlin.math.roundToInt
import com.locationjoystick.core.common.util.randomPointInRadius as geoRandomPointInRadius

private const val TAG = "RoamingEngine"

@Singleton
class RoamingEngine
    @Inject
    constructor(
        private val osrmClient: OsrmClient,
        private val routeInterpolator: RouteInterpolator,
        private val routingErrorReporter: RoutingErrorReporter,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    ) : AutoCloseable {
        private val engineScope = CoroutineScope(SupervisorJob() + dispatcher)

        @Volatile private var activeJob: Job? = null

        @Volatile private var isPaused = false

        /** Live movement speed, read each tick by [walkRouteSegment] so profile changes apply immediately. */
        @Volatile private var currentSpeedMs: Double = 0.0

        fun pauseRoaming() {
            isPaused = true
        }

        fun resumeRoaming() {
            isPaused = false
        }

        /**
         * Updates the speed used by the active roaming walk. Takes effect on the next tick.
         */
        fun updateSpeed(speedMs: Double) {
            currentSpeedMs = speedMs
        }

        internal fun randomPointInRadius(
            center: LatLng,
            radiusMeters: Double,
        ): LatLng = geoRandomPointInRadius(center, radiusMeters)

        /**
         * Plans the full roaming route before walking begins.
         *
         * Straight-line mode generates all waypoints upfront. Road-following mode fetches OSRM
         * segments iteratively until the distance budget is met. Falls back to straight-line on any
         * OSRM failure.
         *
         * Returns a single-element list (just the center) when distanceMeters <= 0 so startRoaming
         * completes immediately with no movement.
         */
        suspend fun planRoute(config: RoamingConfig): List<LatLng> =
            if (config.useRoadSnapping) planRoadFollowingRoute(config) else planStraightLineRoute(config)

        private fun planStraightLineRoute(config: RoamingConfig): List<LatLng> {
            if (config.distanceMeters <= 0.0) return listOf(config.centerPosition)
            val numPoints = max(2, (config.distanceMeters * AppConstants.RoamingConstants.WAYPOINTS_PER_1000M / 1000.0).roundToInt())
            val center = config.centerPosition
            val radius = config.radiusMeters
            val points = mutableListOf(center)
            if (config.returnToInitialLocation) {
                val half = numPoints / 2
                repeat(half) { points.add(randomPointInRadius(center, radius)) }
                repeat(numPoints - half - 1) { points.add(randomPointInRadius(center, radius)) }
                points.add(center)
            } else {
                repeat(numPoints) { points.add(randomPointInRadius(center, radius)) }
            }
            return points
        }

        private suspend fun planRoadFollowingRoute(config: RoamingConfig): List<LatLng> {
            val profile = AppConstants.RoamingConstants.OSRM_PROFILE_FOOT
            val center = config.centerPosition
            val radius = config.radiusMeters
            val target = if (config.returnToInitialLocation) config.distanceMeters / 2.0 else config.distanceMeters
            val allWaypoints = mutableListOf(center)
            var coveredDistance = 0.0
            var callCount = 0
            var totalSegments = 0
            var fallbackCount = 0

            while (coveredDistance < target && callCount < AppConstants.RoamingConstants.MAX_OSRM_PLANNING_CALLS) {
                val dest = randomPointInRadius(center, radius)
                val (result, fellBack) = fetchSegmentOrFallback(profile, allWaypoints.last(), dest)
                allWaypoints.addAll(result.waypoints.drop(1))
                coveredDistance += result.distanceMeters
                callCount++
                totalSegments++
                if (fellBack) fallbackCount++
            }

            if (config.returnToInitialLocation) {
                var returnDistance = 0.0
                while (returnDistance < target && callCount < AppConstants.RoamingConstants.MAX_OSRM_PLANNING_CALLS) {
                    val dest = randomPointInRadius(center, radius)
                    val (result, fellBack) = fetchSegmentOrFallback(profile, allWaypoints.last(), dest)
                    allWaypoints.addAll(result.waypoints.drop(1))
                    returnDistance += result.distanceMeters
                    callCount++
                    totalSegments++
                    if (fellBack) fallbackCount++
                }
                // Final leg: follow roads back to center
                val (result, fellBack) = fetchSegmentOrFallback(profile, allWaypoints.last(), center)
                allWaypoints.addAll(result.waypoints.drop(1))
                totalSegments++
                if (fellBack) fallbackCount++
            }

            if (fallbackCount > 0) {
                routingErrorReporter.report(
                    "Road-following partially unavailable — $fallbackCount of $totalSegments legs used straight-line paths",
                )
            }

            return allWaypoints
        }

        /**
         * Fetches one OSRM segment, falling back to a straight-line two-point segment
         * (with haversine distance) if the request fails. Keeps the planning loop's
         * already-accumulated road segments intact instead of abandoning the whole route.
         * @return the segment result paired with whether it fell back to straight-line.
         */
        private suspend fun fetchSegmentOrFallback(
            profile: String,
            from: LatLng,
            to: LatLng,
        ): Pair<OsrmRouteResult, Boolean> =
            osrmClient.getRouteWithDistance(profile, listOf(from, to)).fold(
                onSuccess = { it to false },
                onFailure = { e ->
                    Log.w(TAG, "OSRM segment failed, falling back to straight-line segment", e)
                    OsrmRouteResult(
                        waypoints = listOf(from, to),
                        distanceMeters = haversineDistance(from, to),
                    ) to true
                },
            )

        /**
         * Starts a roaming session.
         *
         * Uses [config.plannedWaypoints] directly if non-null; otherwise calls [planRoute] inline.
         * Any previously active session is cancelled and awaited before movement begins.
         *
         * @param onRouteUpdate Called once upfront with the full route, once with emptyList on completion.
         * @param onComplete Called when the session ends naturally (not on cancellation).
         * @return Job that can be used to track/cancel the roaming session.
         */
        fun startRoaming(
            config: RoamingConfig,
            speedMs: Double,
            onRouteUpdate: (List<LatLng>) -> Unit = {},
            onComplete: () -> Unit = {},
            onPositionUpdate: (LatLng) -> Unit,
        ): Job {
            val previous = activeJob
            activeJob = null
            currentSpeedMs = speedMs

            val job =
                engineScope.launch {
                    previous?.cancelAndJoin()
                    isPaused = false

                    val route = config.plannedWaypoints?.takeIf { it.size >= 2 } ?: planRoute(config)
                    onRouteUpdate(route)

                    if (route.size >= 2) {
                        walkRouteSegment(route, route.first(), onPositionUpdate)
                    }

                    onRouteUpdate(emptyList())
                    onComplete()
                }
            activeJob = job
            return job
        }

        private suspend fun walkRouteSegment(
            route: List<LatLng>,
            startPosition: LatLng,
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
                        speedMs = currentSpeedMs,
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

        suspend fun stopRoaming() {
            isPaused = false
            val job = activeJob
            activeJob = null
            job?.cancelAndJoin()
        }

        fun stop() {
            val job = activeJob
            activeJob = null
            job?.cancel()
        }

        override fun close() {
            activeJob?.cancel()
            engineScope.cancel()
        }
    }
