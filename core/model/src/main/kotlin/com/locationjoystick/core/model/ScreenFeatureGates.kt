package com.locationjoystick.core.model

/**
 * The one rule behind Tap to Walk and Reconnect Accessory (docs/features/guided-paths.md).
 * "Shown" needs the switch and a guided path that offers the feature. "Active" adds the accessibility
 * disclosure and the API floor of the screenshot both features depend on.
 */
object ScreenFeatureGates {
    /** Equals Build.VERSION_CODES.R; this module has no Build. A widget test pins the match. */
    const val MIN_SDK = 30

    fun tapToWalkShown(
        enabled: Boolean,
        path: GuidedPath,
    ): Boolean = enabled && path.showsTapToWalk

    fun reconnectShown(
        enabled: Boolean,
        path: GuidedPath,
    ): Boolean = enabled && path.showsReconnectAccessory

    fun tapToWalkActive(
        enabled: Boolean,
        accepted: Boolean,
        path: GuidedPath,
        sdkInt: Int,
    ): Boolean = tapToWalkShown(enabled, path) && accepted && sdkInt >= MIN_SDK

    fun reconnectActive(
        enabled: Boolean,
        accepted: Boolean,
        path: GuidedPath,
        sdkInt: Int,
    ): Boolean = reconnectShown(enabled, path) && accepted && sdkInt >= MIN_SDK
}
