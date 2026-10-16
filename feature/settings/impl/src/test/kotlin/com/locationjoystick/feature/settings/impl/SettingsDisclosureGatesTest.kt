package com.locationjoystick.feature.settings.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDisclosureGatesTest {
    @Test
    fun tapToWalkEnableDialogSkippedOnceCompassAccepted() {
        assertTrue(showsTapToWalkEnableDisclosure(compassAccepted = false))
        assertFalse(showsTapToWalkEnableDisclosure(compassAccepted = true))
    }

    @Test
    fun reconnectDialogSkippedWhenEitherFlagAccepted() {
        assertTrue(showsReconnectDisclosure(reconnectAccepted = false, compassAccepted = false))
        assertFalse(showsReconnectDisclosure(reconnectAccepted = true, compassAccepted = false))
        assertFalse(showsReconnectDisclosure(reconnectAccepted = false, compassAccepted = true))
    }

    @Test
    fun compassOpenSettingsDialogSkippedOnceAccepted() {
        assertTrue(showsCompassOpenSettingsDisclosure(compassAccepted = false))
        assertFalse(showsCompassOpenSettingsDisclosure(compassAccepted = true))
    }
}
