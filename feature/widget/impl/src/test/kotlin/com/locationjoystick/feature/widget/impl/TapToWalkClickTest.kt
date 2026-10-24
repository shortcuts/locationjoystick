package com.locationjoystick.feature.widget.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class TapToWalkClickTest {
    @Test
    fun `no disclosure needed opens the overlay`() {
        assertEquals(TapToWalkClick.OPEN_OVERLAY, tapToWalkClick(overlayShowing = false, needsDisclosure = false))
    }

    @Test
    fun `disclosure needed shows the disclosure`() {
        assertEquals(TapToWalkClick.SHOW_DISCLOSURE, tapToWalkClick(overlayShowing = false, needsDisclosure = true))
    }

    @Test
    fun `overlay showing dismisses it`() {
        assertEquals(TapToWalkClick.DISMISS_OVERLAY, tapToWalkClick(overlayShowing = true, needsDisclosure = false))
    }
}
