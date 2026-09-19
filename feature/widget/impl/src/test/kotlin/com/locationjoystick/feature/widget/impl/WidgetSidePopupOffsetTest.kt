package com.locationjoystick.feature.widget.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSidePopupOffsetTest {
    @Test
    fun `places popup to the right of the icon in parent-window space`() {
        assertEquals(
            50 to 250,
            widgetSidePopupOffset(
                parentXOnScreen = 10,
                parentYOnScreen = 180,
                anchorLeft = 0,
                anchorTop = 250,
                anchorRight = 50,
                popupWidth = 200,
                popupHeight = 50,
                screenWidth = 1080,
                screenHeight = 1920,
            ),
        )
    }

    @Test
    fun `flips popup to the left when the widget is docked on the right edge`() {
        assertEquals(
            -200 to 250,
            widgetSidePopupOffset(
                parentXOnScreen = 1030,
                parentYOnScreen = 400,
                anchorLeft = 0,
                anchorTop = 250,
                anchorRight = 50,
                popupWidth = 200,
                popupHeight = 50,
                screenWidth = 1080,
                screenHeight = 1920,
            ),
        )
    }

    @Test
    fun `shifts popup up when it would overflow the bottom of the screen`() {
        assertEquals(
            50 to 70,
            widgetSidePopupOffset(
                parentXOnScreen = 0,
                parentYOnScreen = 1800,
                anchorLeft = 0,
                anchorTop = 200,
                anchorRight = 50,
                popupWidth = 100,
                popupHeight = 50,
                screenWidth = 1080,
                screenHeight = 1920,
            ),
        )
    }
}
