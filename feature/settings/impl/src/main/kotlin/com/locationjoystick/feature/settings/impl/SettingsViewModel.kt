package com.locationjoystick.feature.settings.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.getSpeedProfiles(),
        settingsRepository.getSpeedUnit(),
        settingsRepository.getWidgetFeatures(),
    ) { _, speedUnit, features ->
        SettingsUiState(
            isLoading = false,
            walkSpeed = 1.4,
            runSpeed = 3.0,
            bikeSpeed = 5.5,
            speedUnit = speedUnit,
            enabledWidgetFeatures = features.toSet(),
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(isLoading = true)
        )

    fun setWalkSpeed(displaySpeed: Double) {
        val ms = convertDisplayToMs(displaySpeed, uiState.value.speedUnit)
        viewModelScope.launch {
            settingsRepository.setWalkSpeed(ms)
        }
    }

    fun setRunSpeed(displaySpeed: Double) {
        val ms = convertDisplayToMs(displaySpeed, uiState.value.speedUnit)
        viewModelScope.launch {
            settingsRepository.setRunSpeed(ms)
        }
    }

    fun setBikeSpeed(displaySpeed: Double) {
        val ms = convertDisplayToMs(displaySpeed, uiState.value.speedUnit)
        viewModelScope.launch {
            settingsRepository.setBikeSpeed(ms)
        }
    }

    fun setSpeedUnit(unit: SpeedUnit) {
        viewModelScope.launch {
            settingsRepository.setSpeedUnit(unit)
        }
    }

    fun setWidgetFeatures(features: Set<WidgetFeature>) {
        viewModelScope.launch {
            settingsRepository.setWidgetFeatures(features.toList())
        }
    }

    fun convertMsToDisplay(ms: Double, unit: SpeedUnit): Double {
        return when (unit) {
            SpeedUnit.KMH -> ms * 3.6
            SpeedUnit.MPH -> ms * 2.237
        }
    }

    private fun convertDisplayToMs(displaySpeed: Double, unit: SpeedUnit): Double {
        return when (unit) {
            SpeedUnit.KMH -> displaySpeed / 3.6
            SpeedUnit.MPH -> displaySpeed / 2.237
        }
    }
}
