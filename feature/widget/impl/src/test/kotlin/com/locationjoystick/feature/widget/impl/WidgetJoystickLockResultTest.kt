package com.locationjoystick.feature.widget.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetJoystickLockResultTest {
    @Test
    fun `hidden unlocked shows overlay and locks`() {
        assertEquals(
            WidgetJoystickLockResult(showOverlay = true, locked = true),
            widgetJoystickLockResult(overlayVisible = false, currentlyLocked = false),
        )
    }

    @Test
    fun `hidden already-locked still shows overlay and stays locked`() {
        assertEquals(
            WidgetJoystickLockResult(showOverlay = true, locked = true),
            widgetJoystickLockResult(overlayVisible = false, currentlyLocked = true),
        )
    }

    @Test
    fun `visible unlocked locks in place`() {
        assertEquals(
            WidgetJoystickLockResult(showOverlay = false, locked = true),
            widgetJoystickLockResult(overlayVisible = true, currentlyLocked = false),
        )
    }

    @Test
    fun `visible locked unlocks and leaves overlay up`() {
        assertEquals(
            WidgetJoystickLockResult(showOverlay = false, locked = false),
            widgetJoystickLockResult(overlayVisible = true, currentlyLocked = true),
        )
    }

    @Test
    fun `eye shows overlay when hidden without using lock`() {
        assertEquals(true, widgetJoystickEyeShowsOverlay(overlayVisible = false))
        assertEquals(false, widgetJoystickEyeShowsOverlay(overlayVisible = true))
    }
}
