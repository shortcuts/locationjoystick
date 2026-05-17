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

        private var activeJob: Job? = null

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
                locationRepository.setMockMode(MockMode.TELEPORT)
                locationRepository.setRouteWaypoints(null)
                Log.d(TAG, "Roaming completed or cancelled")
            }
        }

        suspend fun stopRoaming() {
            roamingEngine.stopRoaming()
            activeJob = null
            _isRoaming.value = false
            locationRepository.setMockMode(MockMode.TELEPORT)
            locationRepository.setRouteWaypoints(null)
        }
    }
