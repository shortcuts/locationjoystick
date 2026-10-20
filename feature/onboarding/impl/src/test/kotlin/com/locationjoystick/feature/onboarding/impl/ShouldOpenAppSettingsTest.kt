package com.locationjoystick.feature.onboarding.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldOpenAppSettingsTest {
    @Test
    fun `denied without rationale opens settings`() {
        assertTrue(shouldOpenAppSettings(fineGranted = false, showRationale = false))
    }

    @Test
    fun `denied with rationale asks again`() {
        assertFalse(shouldOpenAppSettings(fineGranted = false, showRationale = true))
    }

    @Test
    fun `granted never opens settings`() {
        assertFalse(shouldOpenAppSettings(fineGranted = true, showRationale = false))
    }
}
