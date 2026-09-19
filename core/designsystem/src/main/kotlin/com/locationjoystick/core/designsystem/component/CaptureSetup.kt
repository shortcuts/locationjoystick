package com.locationjoystick.core.designsystem.component

internal enum class CaptureStepMarker {
    NEEDED,
    DONE,
    ACTION,
    RESTORE,
    IDLE,
}

internal fun captureToggleMarker(enabled: Boolean): CaptureStepMarker = if (enabled) CaptureStepMarker.DONE else CaptureStepMarker.NEEDED

internal fun captureBrowserMarker(isDefaultBrowser: Boolean): CaptureStepMarker =
    if (isDefaultBrowser) CaptureStepMarker.DONE else CaptureStepMarker.NEEDED

internal fun captureRestoreMarker(functionEnabled: Boolean): CaptureStepMarker =
    if (!functionEnabled) CaptureStepMarker.RESTORE else CaptureStepMarker.IDLE

internal fun isCaptureReady(
    captureModeEnabled: Boolean,
    captureEnabled: Boolean,
    jumpEnabled: Boolean,
    isDefaultBrowser: Boolean,
): Boolean = captureModeEnabled && (captureEnabled || jumpEnabled) && isDefaultBrowser
