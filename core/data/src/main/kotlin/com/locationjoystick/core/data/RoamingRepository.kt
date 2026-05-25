package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.routing.RoamingEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private const val TAG = "RoamingRepository"

@Singleton
class RoamingRepository
    @Inject
    constructor(
        private val roamingEngine: RoamingEngine,
        private val locationRepository: LocationRepository,
    ) {
        private val _isRoaming = MutableStateFlow(false)
        val isRoaming: StateFlow<Boolean> = _isRoaming.asStateFlow()

        private val _isRoamingPaused = MutableStateFlow(false)
        val isRoamingPaused: StateFlow<Boolean> = _isRoamingPaused.asStateFlow()

        private var activeJob: Job? = null

        fun pauseRoaming() {
            roamingEngine.pauseRoaming()
            _isRoamingPaused.value = true
        }

        fun resumeRoaming() {
            roamingEngine.resumeRoaming()
            _isRoamingPaused.value = false
        }

        fun startRoaming(
            config: RoamingConfig,
            speedMs: Double,
        ) {
            activeJob?.cancel()
            Log.d(
                TAG,
                "Starting roaming: radius=${config.radiusMeters}m, distance=${config.distanceMeters}m, profile=${config.speedProfileId}",
            )
            _isRoaming.value = true
            locationRepository.setMockMode(MockMode.ROAMING)
            activeJob =
                roamingEngine.startRoaming(
                    config = config,
                    speedMs = speedMs,
                    onPositionUpdate = { position ->
                        locationRepository.setPositionInternal(position)
                    },
                    onRouteUpdate = { waypoints ->
                        locationRepository.setRouteWaypoints(waypoints.ifEmpty { null })
                    },
                )
            activeJob?.invokeOnCompletion {
                _isRoaming.value = false
                _isRoamingPaused.value = false
                locationRepository.setMockMode(MockMode.TELEPORT)
                locationRepository.setRouteWaypoints(null)
                Log.d(TAG, "Roaming completed or cancelled")
            }
        }

        /**
         * Generates a preview route for display on the map without starting actual roaming.
         * Applies ±2% random jitter to center position and radius for variety on repeated calls.
         */
        suspend fun generatePreviewRoute(
            center: LatLng,
            radiusMeters: Double,
            followRoads: Boolean,
            speedProfileId: String,
        ): List<LatLng> {
            val jitter = 0.02
            val jitteredRadius = radiusMeters * (1.0 + Random.nextDouble(-jitter, jitter))
            val jitteredCenter = roamingEngine.randomPointInRadius(center, radiusMeters * jitter)
            val destination = roamingEngine.randomPointInRadius(jitteredCenter, jitteredRadius)
            return roamingEngine.previewRoute(jitteredCenter, destination, followRoads, speedProfileId)
        }

        suspend fun stopRoaming() {
            roamingEngine.stopRoaming()
            activeJob = null
            _isRoaming.value = false
            _isRoamingPaused.value = false
            locationRepository.setMockMode(MockMode.TELEPORT)
            locationRepository.setRouteWaypoints(null)
        }

        /**
         * Cancels any active roaming job and resets state without destroying the engine scope.
         * Safe to call from service onDestroy — the engine remains reusable after service restart.
         */
        fun resetOnServiceDestroy() {
            roamingEngine.resumeRoaming()
            activeJob?.cancel()
            activeJob = null
            _isRoaming.value = false
            _isRoamingPaused.value = false
            locationRepository.setMockMode(MockMode.TELEPORT)
            locationRepository.setRouteWaypoints(null)
        }
    }
