package com.locationjoystick.core.designsystem.component

import com.locationjoystick.core.common.constants.AppConstants

internal fun startToggleLabel(
    isSpoofing: Boolean,
    locationLabel: String?,
    maxLocationChars: Int = AppConstants.TopBarConstants.LOCATION_LABEL_MAX_CHARS,
): String {
    if (isSpoofing) return "Stop"
    val label = locationLabel?.trim().orEmpty()
    if (label.isEmpty()) return "Start"
    val clipped = if (label.length <= maxLocationChars) label else label.take(maxLocationChars)
    return "Start · $clipped"
}
