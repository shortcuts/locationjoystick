package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.routing.RoamingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

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

        fun pauseRoaming() {
            roamingEngine.pauseRoaming()
            _isRoamingPaused.value = true
        }

        fun resumeRoaming() {
            roamingEngine.resumeRoaming()
            _isRoamingPaused.value = false
        }

        /** Propagates a live speed-profile change into the active roaming walk. */
        fun updateSpeed(speedMs: Double) {
            roamingEngine.updateSpeed(speedMs)
        }

        fun startRoaming(
            config: RoamingConfig,
            speedMs: Double,
        ) {
            Log.d(
                TAG,
                "Starting roaming: radius=${config.radiusMeters}m, distance=${config.distanceMeters}m, profile=${config.speedProfileId}",
            )
            _isRoaming.value = true
            locationRepository.setMockMode(MockMode.ROAMING)
            roamingEngine.startRoaming(
                config = config,
                speedMs = speedMs,
                onPositionUpdate = { position ->
                    locationRepository.setPositionInternal(position)
                },
                onRouteUpdate = { waypoints ->
                    locationRepository.setRouteWaypoints(waypoints.ifEmpty { null })
                },
                onComplete = {
                    _isRoaming.value = false
                    _isRoamingPaused.value = false
                    locationRepository.setMockMode(MockMode.TELEPORT)
                    locationRepository.setRouteWaypoints(null)
                    locationRepository.emitCompletion("Roaming complete")
                    Log.d(TAG, "Roaming completed or cancelled")
                },
            )
        }

        /** Plans the full roaming route without starting any session. */
        suspend fun planRoute(config: RoamingConfig): List<LatLng> = roamingEngine.planRoute(config)

        suspend fun stopRoaming() {
            roamingEngine.stopRoaming()
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
            roamingEngine.stop()
            _isRoaming.value = false
            _isRoamingPaused.value = false
            locationRepository.setMockMode(MockMode.TELEPORT)
            locationRepository.setRouteWaypoints(null)
        }
    }
