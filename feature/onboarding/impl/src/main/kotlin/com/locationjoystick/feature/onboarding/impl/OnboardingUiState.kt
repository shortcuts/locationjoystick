package com.locationjoystick.feature.onboarding.impl

data class OnboardingUiState(
    val locationPermissionGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val mockLocationEnabled: Boolean = false,
    val isDebugBuild: Boolean = false,
)

val OnboardingUiState.canProceed: Boolean
    get() = locationPermissionGranted && overlayPermissionGranted && mockLocationEnabled
