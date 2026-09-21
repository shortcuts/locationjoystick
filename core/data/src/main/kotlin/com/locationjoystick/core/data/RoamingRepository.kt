package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.common.util.BearingTracker
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.model.canStartRoaming
import com.locationjoystick.core.routing.RoamingEngine
import com.locationjoystick.core.routing.RouteReplayEngine
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
        private val routeReplayEngine: RouteReplayEngine,
    ) {
        private val _isRoaming = MutableStateFlow(false)
        val isRoaming: StateFlow<Boolean> = _isRoaming.asStateFlow()

        private val _isRoamingPaused = MutableStateFlow(false)
        val isRoamingPaused: StateFlow<Boolean> = _isRoamingPaused.asStateFlow()

        private val bearingTracker = BearingTracker()

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
            locationRepository.setSpeedInternal(speedMs.toFloat())
        }

        private fun resetRoamingState() {
            _isRoaming.value = false
            _isRoamingPaused.value = false
            locationRepository.setMockMode(MockMode.TELEPORT)
            locationRepository.setRouteWaypoints(null)
            locationRepository.setSpeedInternal(0f)
        }

        /**
         * Starts roaming if a playing route does not own the tick.
         *
         * A paused route is stopped first (engine halt + metadata clear) so replay cannot keep
         * writing after mode flips to [MockMode.ROAMING]. Returns false when a route is playing.
         */
        suspend fun startRoaming(
            config: RoamingConfig,
            speedMs: Double,
        ): Boolean {
            val mode = locationRepository.currentMode.value
            val state = locationRepository.mockLocationState.value
            if (!canStartRoaming(mode, state)) {
                Log.w(TAG, "Refusing roaming start: a route is playing")
                return false
            }
            if (mode == MockMode.ROUTE_REPLAY) {
                routeReplayEngine.stop()
                locationRepository.setRouteWaypoints(null)
                locationRepository.setRouteProgress(null)
                locationRepository.setActiveRouteId(null)
                if (state == MockLocationState.PAUSED) {
                    locationRepository.startSpoofing()
                }
            }
            Log.d(
                TAG,
                "Starting roaming: radius=${config.radiusMeters}m, distance=${config.distanceMeters}m, profile=${config.speedProfileId}",
            )
            _isRoaming.value = true
            locationRepository.setMockMode(MockMode.ROAMING)
            locationRepository.setSpeedInternal(speedMs.toFloat())
            roamingEngine.startRoaming(
                config = config,
                speedMs = speedMs,
                onPositionUpdate = { position ->
                    bearingTracker.advance(position)?.let { locationRepository.setBearingInternal(it) }
                    locationRepository.setPositionInternal(position)
                },
                onRouteUpdate = { waypoints ->
                    locationRepository.setRouteWaypoints(waypoints.ifEmpty { null })
                },
                onComplete = {
                    resetRoamingState()
                    locationRepository.emitCompletion("Roaming complete")
                    Log.d(TAG, "Roaming completed or cancelled")
                },
            )
            return true
        }

        /** Plans the full roaming route without starting any session. */
        suspend fun planRoute(config: RoamingConfig): List<LatLng> = roamingEngine.planRoute(config)

        suspend fun stopRoaming() {
            roamingEngine.stopRoaming()
            resetRoamingState()
        }

        /**
         * Cancels any active roaming job and resets state without destroying the engine scope.
         * Safe to call from service onDestroy — the engine remains reusable after service restart.
         */
        fun resetOnServiceDestroy() {
            roamingEngine.resumeRoaming()
            roamingEngine.stop()
            resetRoamingState()
        }
    }
