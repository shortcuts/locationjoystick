package com.locationjoystick.feature.onboarding.impl

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.LocaleContextWrapper
import com.locationjoystick.core.common.util.isMockLocationEnabled
import com.locationjoystick.core.common.util.isOverlayPermissionGranted
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.GuidedPath
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

        private val _languageTag = MutableStateFlow(LocaleContextWrapper.getLanguage(context))
        val languageTag: StateFlow<String?> = _languageTag.asStateFlow()

        fun setLanguage(tag: String?) {
            LocaleContextWrapper.setLanguage(context, tag)
            _languageTag.value = tag
        }

        init {
            checkPermissions()
            viewModelScope.launch {
                settingsRepository.getAccessibilityDisclosureChoice().collect { choice ->
                    _uiState.update { it.copy(accessibilityDisclosureChoice = choice) }
                }
            }
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
                        screenServiceEnabled = isScreenServiceEnabled(),
                    )
                }
            }
        }

        private fun isScreenServiceEnabled(): Boolean =
            context
                .getSystemService(AccessibilityManager::class.java)
                ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.any { it.id.contains("ScreenAccessibilityService") }
                ?: false

        fun recordAccessibilityDisclosure(accepted: Boolean) {
            viewModelScope.launch { settingsRepository.recordAccessibilityDisclosure(accepted) }
        }

        /** Marks the prompt as asked without consenting; Tap to Walk entry points still re-show it. */
        fun skipAccessibilityDisclosure() {
            viewModelScope.launch {
                settingsRepository.setAccessibilityDisclosureChoice(AppConstants.AccessibilityConstants.DISCLOSURE_SKIPPED)
            }
        }

        fun selectPath(path: GuidedPath) {
            _uiState.update { it.copy(selectedPath = path) }
        }

        /** Runs [onDone] after the flag is written: navigating first clears this ViewModel and cancels the write. */
        fun onSetupComplete(onDone: () -> Unit) {
            viewModelScope.launch {
                settingsRepository.setGuidedPath(_uiState.value.selectedPath)
                settingsRepository.setOnboardingComplete(true)
                onDone()
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
