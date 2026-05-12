package com.locationjoystick.feature.roaming.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.routing.RoamingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class RoamingViewModel
    @Inject
    constructor(
        private val roamingEngine: RoamingEngine,
        private val locationRepository: LocationRepository,
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

        fun startRoaming() {
            // TODO: implement roaming start logic
        }

        fun stopRoaming() {
            // TODO: implement roaming stop logic
        }
    }
