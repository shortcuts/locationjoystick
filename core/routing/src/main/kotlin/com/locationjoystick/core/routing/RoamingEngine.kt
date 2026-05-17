package com.locationjoystick.core.routing

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

private const val TAG = "RoamingEngine"

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
            val dLat = r * cos(theta) / AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
            val dLon = r * sin(theta) / (AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE * cos(Math.toRadians(center.latitude)))
            return LatLng(center.latitude + dLat, center.longitude + dLon)
        }

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

                    while (isActive && remainingMeters > 0) {
                        if (currentRoute.isEmpty() || waypointIndex >= currentRoute.size) {
                            val destination = randomPointInRadius(config.centerPosition, config.radiusMeters)
                            currentRoute = fetchRoute(config, currentPosition, destination)
                            onRouteUpdate(currentRoute)
                            waypointIndex = 0
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
                        remainingMeters -= speedMs

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
                        while (isActive && !returnRoute.isEmpty()) {
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
