package com.locationjoystick.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavGateReachabilityTest {
    @Test
    fun `first run with core permissions granted is held on onboarding for the path picker`() {
        assertFalse(isNavGateReachable(onboardingComplete = false, coreGranted = true))
    }

    @Test
    fun `returning user with core permissions reaches the app even without overlay`() {
        assertTrue(isNavGateReachable(onboardingComplete = true, coreGranted = true))
    }

    @Test
    fun `returning user missing core permissions stays blocked`() {
        assertFalse(isNavGateReachable(onboardingComplete = true, coreGranted = false))
    }
}
