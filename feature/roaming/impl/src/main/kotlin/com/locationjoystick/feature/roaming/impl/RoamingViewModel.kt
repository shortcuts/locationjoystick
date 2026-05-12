package com.locationjoystick.feature.roaming.impl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.datastore.AppPreferencesDataSource
import com.locationjoystick.core.model.RoamingConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "RoamingViewModel"

@HiltViewModel
class RoamingViewModel
    @Inject
    constructor(
        private val roamingRepository: RoamingRepository,
        private val preferencesDataSource: AppPreferencesDataSource,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RoamingUiState())
        val uiState: StateFlow<RoamingUiState> = _uiState.asStateFlow()

        fun updateRadius(meters: Double) {
            _uiState.update { it.copy(config = it.config.copy(radiusMeters = meters)) }
        }

        fun updateDuration(minutes: Int) {
            _uiState.update { it.copy(config = it.config.copy(durationSeconds = (minutes * 60L))) }
        }

        fun toggleOsrmRouting(enabled: Boolean) {
            _uiState.update { it.copy(config = it.config.copy(useRoadSnapping = enabled)) }
        }

        fun updateTransportMode(mode: String) {
            _uiState.update { it.copy(transportMode = mode) }
            viewModelScope.launch {
                preferencesDataSource.setRoamingTransportMode(mode)
            }
        }

        fun startRoaming() {
            viewModelScope.launch {
                try {
                    val speedProfiles = preferencesDataSource.getSpeedProfiles().firstOrNull() ?: return@launch
                    val roamingPrefs = preferencesDataSource.getRoamingConfig().firstOrNull() ?: return@launch

                    val speedMs =
                        when (roamingPrefs.transportMode) {
                            "walk" -> speedProfiles.walkSpeedMs
                            "run" -> speedProfiles.runSpeedMs
                            "bike" -> speedProfiles.bikeSpeedMs
                            else -> speedProfiles.walkSpeedMs
                        }

                    val config =
                        _uiState.value.config.copy(
                            centerPosition = roamingPrefs.centerPosition,
                            radiusMeters = roamingPrefs.radiusMeters,
                            durationSeconds = roamingPrefs.durationSeconds,
                            useRoadSnapping = roamingPrefs.roadFollowing,
                        )

                    roamingRepository.startRoaming(config, speedMs, roamingPrefs.transportMode)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start roaming", e)
                }
            }
        }

        fun stopRoaming() {
            viewModelScope.launch {
                roamingRepository.stopRoaming()
            }
        }
    }
