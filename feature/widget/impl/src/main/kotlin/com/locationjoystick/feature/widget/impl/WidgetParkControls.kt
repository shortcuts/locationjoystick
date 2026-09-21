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

/**
 * Feature icons, extra sections, and pickers only work while mock GPS is running.
 * Pause fades them and makes taps a no-op until the user taps Start.
 */
internal fun widgetControlsEnabled(spoofingActive: Boolean): Boolean = spoofingActive

/** Stop on the long-press popup is never faded or ignored, including while parked. */
internal fun widgetStopEnabled(): Boolean = true

/** Route icon (green, pause/stop popup) is for route replay or walk-to, never roaming; roaming has its own icon. */
internal fun routeControlsActive(mode: MockMode): Boolean = mode == MockMode.ROUTE_REPLAY || mode == MockMode.WALK_TO
