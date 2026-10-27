package com.locationjoystick.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.MockLocationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Exposes the mock-location bypass setting, the onboarding-complete flag and the spoofing state so
 * [LjNavHost] and [com.locationjoystick.app.LjApp] can pick the right entry destination.
 */
@HiltViewModel
class NavGateViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        locationRepository: LocationRepository,
    ) : ViewModel() {
        val mockLocationState: StateFlow<MockLocationState> = locationRepository.mockLocationState

        suspend fun isOnboardingComplete(): Boolean = settingsRepository.getOnboardingComplete().first()

        val bypassMockLocationCheck: StateFlow<Boolean> =
            settingsRepository.getBypassMockLocationCheck().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

        val onboardingComplete: StateFlow<Boolean> =
            settingsRepository.getOnboardingComplete().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )
    }
