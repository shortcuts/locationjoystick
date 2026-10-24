package com.locationjoystick.core.model

/**
 * The latest flow-measured map plus the screen it was measured on. Valid only on that screen size and the
 * camera state that produced it, so it is never exported (per-device).
 */
data class StoredGroundCalibration(
    val calibration: GroundCalibration,
    /** Screenshot size in device pixels the walk was captured at. */
    val screenWidth: Int,
    val screenHeight: Int,
    val calibratedAtMs: Long,
    /** Fit quality: crop flows used and their median absolute residual (device px). */
    val flowCount: Int,
    val medianAbsPx: Double,
) {
    fun matches(
        width: Int,
        height: Int,
    ): Boolean = width == screenWidth && height == screenHeight
}
