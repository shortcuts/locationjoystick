package com.locationjoystick.feature.widget.impl

internal enum class TapToWalkClick { OPEN_OVERLAY, SHOW_DISCLOSURE, DISMISS_OVERLAY }

internal fun tapToWalkClick(
    overlayShowing: Boolean,
    needsDisclosure: Boolean,
): TapToWalkClick =
    when {
        overlayShowing -> TapToWalkClick.DISMISS_OVERLAY
        needsDisclosure -> TapToWalkClick.SHOW_DISCLOSURE
        else -> TapToWalkClick.OPEN_OVERLAY
    }
