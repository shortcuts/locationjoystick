package com.locationjoystick.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** Icon color state shared by widget, map FABs and floating map: grey idle, green moving, orange paused. */
enum class ActivityState { IDLE, MOVING, PAUSED }

fun activityState(
    active: Boolean,
    paused: Boolean,
): ActivityState =
    when {
        active && paused -> ActivityState.PAUSED
        active -> ActivityState.MOVING
        else -> ActivityState.IDLE
    }

/** Filled-button container (map FABs, floating map). */
fun ActivityState.containerColor(scheme: ColorScheme): Color =
    when (this) {
        ActivityState.IDLE -> scheme.surfaceVariant
        ActivityState.MOVING -> LjSuccess
        ActivityState.PAUSED -> scheme.primary
    }

fun ActivityState.contentColor(scheme: ColorScheme): Color =
    when (this) {
        ActivityState.IDLE -> scheme.onSurfaceVariant
        else -> LjBg
    }
