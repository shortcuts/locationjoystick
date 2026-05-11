package com.locationjoystick.feature.setup.impl

data class SetupUiState(
    val locationPermissionGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val mockLocationEnabled: Boolean = false,
)

val SetupUiState.canProceed: Boolean
    get() = locationPermissionGranted && overlayPermissionGranted && mockLocationEnabled
