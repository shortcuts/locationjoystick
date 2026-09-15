package com.locationjoystick.feature.widget.impl

/**
 * Result of tapping the widget lock button.
 *
 * Overlay visibility and lock are independent of whether joystick *movement* is
 * ignored (a playing route or running roam owns the tick). The stick stays faded
 * in that case; lock still shows it so it is ready when the owning session pauses.
 *
 * @param showOverlay true when the overlay is hidden and must be shown first
 * @param locked the lock state to apply after the press
 */
internal data class WidgetJoystickLockResult(
    val showOverlay: Boolean,
    val locked: Boolean,
)

/**
 * Hidden overlay → show and lock. Visible overlay → toggle lock in place.
 */
internal fun widgetJoystickLockResult(
    overlayVisible: Boolean,
    currentlyLocked: Boolean,
): WidgetJoystickLockResult =
    if (!overlayVisible) {
        WidgetJoystickLockResult(showOverlay = true, locked = true)
    } else {
        WidgetJoystickLockResult(showOverlay = false, locked = !currentlyLocked)
    }

/** Eye button shows the overlay when it is hidden; independent of lock. */
internal fun widgetJoystickEyeShowsOverlay(overlayVisible: Boolean): Boolean = !overlayVisible
