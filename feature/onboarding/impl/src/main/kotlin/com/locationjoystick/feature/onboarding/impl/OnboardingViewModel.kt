package com.locationjoystick.feature.onboarding.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.util.isMockLocationEnabled
import com.locationjoystick.core.common.util.isOverlayPermissionGranted
import com.locationjoystick.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OnboardingUiState(isDebugBuild = BuildConfig.DEBUG))
        val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        init {
            checkPermissions()
        }

        fun checkPermissions() {
            viewModelScope.launch {
                val bypassMockLocationCheck = settingsRepository.getBypassMockLocationCheck().first()
                _uiState.update { current ->
                    current.copy(
                        locationPermissionGranted =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED,
                        overlayPermissionGranted = isOverlayPermissionGranted(context),
                        mockLocationEnabled = bypassMockLocationCheck || isMockLocationEnabled(context),
                    )
                }
            }
        }

        fun onSetupComplete() {
            viewModelScope.launch {
                settingsRepository.setOnboardingComplete(true)
            }
        }

        /** Modified mock-location setups can evade the AppOpsManager check even though spoofing works. */
        fun skipMockLocationCheck() {
            viewModelScope.launch {
                settingsRepository.setBypassMockLocationCheck(true)
                checkPermissions()
            }
        }
    }
