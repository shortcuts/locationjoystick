package com.locationjoystick.core.routing

import android.util.Log
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.model.bearingTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "RoamingEngine"
private const val TICK_INTERVAL_MS = 1000L
private const val OSRM_PROFILE_FOOT = "foot"
private const val OSRM_PROFILE_CYCLING = "cycling"
private const val WAYPOINT_ARRIVAL_THRESHOLD_METERS = 5.0

@Singleton
class RoamingEngine
    @Inject
    constructor(
        private val osrmClient: OsrmClient,
        private val routeInterpolator: RouteInterpolator,
    ) {
        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var activeJob: Job? = null

        fun randomPointInRadius(
            center: LatLng,
            radiusMeters: Double,
        ): LatLng {
            val r = radiusMeters * sqrt(Math.random())
            val theta = Math.random() * 2 * PI
            val dLat = r * cos(theta) / 111_320.0
            val dLon = r * sin(theta) / (111_320.0 * cos(Math.toRadians(center.latitude)))
            return LatLng(center.latitude + dLat, center.longitude + dLon)
        }

        fun startRoaming(
            config: RoamingConfig,
            speedMs: Double,
            onPositionUpdate: (LatLng) -> Unit,
        ): Job {
            activeJob?.cancel()
            val job =
                engineScope.launch {
                    val startTime = System.currentTimeMillis()
                    val durationMs = config.durationSeconds * 1000L
                    var currentPosition = config.centerPosition
                    var currentRoute: List<LatLng> = emptyList()
                    var waypointIndex = 0

                    while (isActive && (System.currentTimeMillis() - startTime) < durationMs) {
                        if (currentRoute.isEmpty() || waypointIndex >= currentRoute.size) {
                            val destination = randomPointInRadius(config.centerPosition, config.radiusMeters)
                            currentRoute = fetchRoute(config, currentPosition, destination)
                            waypointIndex = 0
                        }

                        val result =
                            routeInterpolator.interpolateAlongRoute(
                                waypoints = currentRoute,
                                currentPosition = currentPosition,
                                currentWaypointIndex = waypointIndex,
                                speedMs = speedMs,
                                deltaTimeMs = TICK_INTERVAL_MS,
                            )

                        currentPosition = result.position
                        waypointIndex = result.nextWaypointIndex

                        if (result.reachedEnd) {
                            currentRoute = emptyList()
                        }

                        onPositionUpdate(currentPosition)
                        delay(TICK_INTERVAL_MS)
                    }
                }
            activeJob = job
            return job
        }

        suspend fun stopRoaming() {
            activeJob?.cancelAndJoin()
            activeJob = null
        }

        private suspend fun fetchRoute(
            config: RoamingConfig,
            from: LatLng,
            to: LatLng,
        ): List<LatLng> {
            if (!config.useRoadSnapping) {
                return osrmClient.straightLineRoute(from, to)
            }

            val profile = if (config.radiusMeters > 1000) OSRM_PROFILE_CYCLING else OSRM_PROFILE_FOOT
            return osrmClient
                .getRoute(profile, listOf(from, to))
                .getOrElse { e ->
                    Log.w(TAG, "OSRM unavailable, using straight-line fallback", e)
                    osrmClient.straightLineRoute(from, to)
                }
        }
    }
