package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.location.LearningState
import org.junit.Assert.assertEquals
import org.junit.Test

class TapToWalkClickTest {
    @Test
    fun `learning in progress cancels learning and never opens the overlay`() {
        val learning = LearningState.Learning(1, 3)
        assertEquals(TapToWalkClick.CANCEL_LEARNING, tapToWalkClick(learning, overlayShowing = false, needsDisclosure = false))
        assertEquals(TapToWalkClick.CANCEL_LEARNING, tapToWalkClick(learning, overlayShowing = false, needsDisclosure = true))
    }

    @Test
    fun `idle with no disclosure needed opens the overlay`() {
        assertEquals(TapToWalkClick.OPEN_OVERLAY, tapToWalkClick(LearningState.Idle, overlayShowing = false, needsDisclosure = false))
    }

    @Test
    fun `idle with disclosure needed shows the disclosure`() {
        assertEquals(TapToWalkClick.SHOW_DISCLOSURE, tapToWalkClick(LearningState.Idle, overlayShowing = false, needsDisclosure = true))
    }

    @Test
    fun `overlay showing dismisses it`() {
        assertEquals(TapToWalkClick.DISMISS_OVERLAY, tapToWalkClick(LearningState.Idle, overlayShowing = true, needsDisclosure = false))
    }
}
