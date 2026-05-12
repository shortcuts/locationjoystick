package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.model.LatLng
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
            transportMode: String,
        ) {
            activeJob?.cancel()
            Log.d(TAG, "Starting roaming: radius=${config.radiusMeters}m, duration=${config.durationSeconds}s, transport=$transportMode")
            _isRoaming.value = true
            activeJob =
                roamingEngine.startRoaming(
                    config = config,
                    speedMs = speedMs,
                    transportMode = transportMode,
                    onPositionUpdate = { position ->
                        locationRepository.setPositionInternal(position)
                    },
                )
            activeJob?.invokeOnCompletion {
                _isRoaming.value = false
                Log.d(TAG, "Roaming completed or cancelled")
            }
        }

        suspend fun stopRoaming() {
            roamingEngine.stopRoaming()
            activeJob = null
            _isRoaming.value = false
        }
    }
