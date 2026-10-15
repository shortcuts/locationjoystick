package com.locationjoystick.core.model

/**
 * The latest successful calibration walk: the fitted map plus the screen it was fitted on. Valid only on
 * that screen size and the camera state that produced it, so it is never exported (per-device).
 */
data class StoredGroundCalibration(
    val calibration: GroundCalibration,
    /** Screenshot size in device pixels the walk was captured at. */
    val screenWidth: Int,
    val screenHeight: Int,
    val calibratedAtMs: Long,
    /** Fit quality for staleness checks: kept band flows and their median absolute residual (reference px). */
    val flowCount: Int,
    val medianAbsPx: Double,
) {
    fun matches(
        width: Int,
        height: Int,
    ): Boolean = width == screenWidth && height == screenHeight
}
