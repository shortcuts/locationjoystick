package com.locationjoystick.feature.settings.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDisclosureGatesTest {
    @Test
    fun tapToWalkEnableDialogSkippedOnceAccessibilityAccepted() {
        assertTrue(showsTapToWalkEnableDisclosure(accessibilityAccepted = false))
        assertFalse(showsTapToWalkEnableDisclosure(accessibilityAccepted = true))
    }

    @Test
    fun tapToWalkToggleEnablesWhenAccessibilityAccepted() {
        assertEquals(true, tapToWalkToggleResult(on = true, accessibilityAccepted = true))
        assertNull(tapToWalkToggleResult(on = true, accessibilityAccepted = false))
        assertEquals(false, tapToWalkToggleResult(on = false, accessibilityAccepted = true))
        assertEquals(false, tapToWalkToggleResult(on = false, accessibilityAccepted = false))
    }

    @Test
    fun reconnectDialogSkippedWhenEitherFlagAccepted() {
        assertTrue(showsReconnectDisclosure(reconnectAccepted = false, accessibilityAccepted = false))
        assertFalse(showsReconnectDisclosure(reconnectAccepted = true, accessibilityAccepted = false))
        assertFalse(showsReconnectDisclosure(reconnectAccepted = false, accessibilityAccepted = true))
    }

    @Test
    fun accessibilityOpenSettingsDialogSkippedOnceAccepted() {
        assertTrue(showsAccessibilityOpenSettingsDisclosure(accessibilityAccepted = false))
        assertFalse(showsAccessibilityOpenSettingsDisclosure(accessibilityAccepted = true))
    }

    @Test
    fun fullDisclosureShownWheneverUnacceptedEvenIfServiceAlreadyGranted() {
        assertTrue(showsFullAccessibilityDisclosure(sdkInt = 30, accessibilityAccepted = false))
        assertFalse(showsFullAccessibilityDisclosure(sdkInt = 30, accessibilityAccepted = true))
        assertFalse(showsFullAccessibilityDisclosure(sdkInt = 29, accessibilityAccepted = false))
    }
}
