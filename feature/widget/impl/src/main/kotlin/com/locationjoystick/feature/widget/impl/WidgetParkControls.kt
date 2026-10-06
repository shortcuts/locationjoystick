package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.model.MockMode

/** Long-press popup next to the widget app icon. */
internal enum class WidgetMasterPopupMode {
    /** Spoofing is on: Pause parks mock GPS and keeps the widget; Stop tears everything down. */
    PAUSE_AND_STOP,

    /** Parked: Start resumes spoofing; Stop still tears the widget down. */
    START_AND_STOP,
}

internal fun widgetMasterPopupMode(spoofingActive: Boolean): WidgetMasterPopupMode =
    if (spoofingActive) WidgetMasterPopupMode.PAUSE_AND_STOP else WidgetMasterPopupMode.START_AND_STOP

/** Route icon (green, pause/stop popup) is for route replay or walk-to, never roaming; roaming has its own icon. */
internal fun routeControlsActive(mode: MockMode): Boolean = mode == MockMode.ROUTE_REPLAY || mode == MockMode.WALK_TO

/** Widget icon color state: grey idle, green moving, orange paused. */
internal enum class WidgetActivityState { IDLE, MOVING, PAUSED }

internal fun widgetActivityState(
    active: Boolean,
    paused: Boolean,
): WidgetActivityState =
    when {
        active && paused -> WidgetActivityState.PAUSED
        active -> WidgetActivityState.MOVING
        else -> WidgetActivityState.IDLE
    }

/** The stick is actively moving (an unlocked release or a stop resets the mode to TELEPORT). */
internal fun joystickMoving(mode: MockMode): Boolean = mode == MockMode.JOYSTICK
