package com.locationjoystick.feature.onboarding.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowsTapToWalkAccessCardTest {
    @Test
    fun `hidden below API 30 even when the path shows Tap to Walk`() {
        assertFalse(showsTapToWalkAccessCard(showsTapToWalk = true, sdkInt = 29))
    }

    @Test
    fun `shown on API 30 when the path shows Tap to Walk`() {
        assertTrue(showsTapToWalkAccessCard(showsTapToWalk = true, sdkInt = 30))
    }

    @Test
    fun `hidden when the path hides Tap to Walk`() {
        assertFalse(showsTapToWalkAccessCard(showsTapToWalk = false, sdkInt = 34))
    }
}
