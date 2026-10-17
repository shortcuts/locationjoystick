package com.locationjoystick.feature.settings.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDisclosureGatesTest {
    @Test
    fun tapToWalkEnableDialogSkippedOnceAccessibilityAccepted() {
        assertTrue(showsTapToWalkEnableDisclosure(accessibilityAccepted = false))
        assertFalse(showsTapToWalkEnableDisclosure(accessibilityAccepted = true))
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
}
