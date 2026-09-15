package com.locationjoystick.feature.widget.impl

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
@Suppress("UNUSED_PARAMETER")
internal fun widgetStopEnabled(spoofingActive: Boolean): Boolean = true
