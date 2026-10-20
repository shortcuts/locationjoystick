package com.locationjoystick.feature.onboarding.impl

import com.locationjoystick.core.model.GuidedPath

data class OnboardingUiState(
    val locationPermissionGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val mockLocationEnabled: Boolean = false,
    val isDebugBuild: Boolean = false,
    // Optional step on the path picker; never required to proceed.
    val screenServiceEnabled: Boolean = false,
    // Null until DataStore loads, so an already-answered install never sees the first-run prompt flash.
    val accessibilityDisclosureChoice: String? = null,
    val selectedPath: GuidedPath = GuidedPath.DEFAULT,
)

val OnboardingUiState.canProceed: Boolean
    get() = locationPermissionGranted && overlayPermissionGranted && mockLocationEnabled

/** Android stops showing the permission dialog after repeated denials, so only app settings can grant it. */
internal fun shouldOpenAppSettings(
    fineGranted: Boolean,
    showRationale: Boolean,
): Boolean = !fineGranted && !showRationale
