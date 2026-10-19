package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.location.LearningState

internal enum class TapToWalkClick { OPEN_OVERLAY, CANCEL_LEARNING, SHOW_DISCLOSURE, DISMISS_OVERLAY }

// Learning (including the analysis wait) wins: the overlay would walk and replace the learning leg, which fails the learn.
internal fun tapToWalkClick(
    learning: LearningState,
    overlayShowing: Boolean,
    needsDisclosure: Boolean,
): TapToWalkClick =
    when {
        learning != LearningState.Idle -> TapToWalkClick.CANCEL_LEARNING
        overlayShowing -> TapToWalkClick.DISMISS_OVERLAY
        needsDisclosure -> TapToWalkClick.SHOW_DISCLOSURE
        else -> TapToWalkClick.OPEN_OVERLAY
    }
