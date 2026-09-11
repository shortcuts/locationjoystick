package com.locationjoystick.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavGateReachabilityTest {
    @Test
    fun `returning user with overlay revoked still reaches the app`() {
        assertTrue(
            isNavGateReachable(onboardingComplete = true, coreGranted = true, overlayGranted = false),
        )
    }

    @Test
    fun `returning user missing core permissions stays blocked`() {
        assertFalse(
            isNavGateReachable(onboardingComplete = true, coreGranted = false, overlayGranted = true),
        )
    }

    @Test
    fun `fresh install still requires the overlay permission`() {
        assertFalse(
            isNavGateReachable(onboardingComplete = false, coreGranted = true, overlayGranted = false),
        )
    }

    @Test
    fun `fresh install with everything granted reaches the app`() {
        assertTrue(
            isNavGateReachable(onboardingComplete = false, coreGranted = true, overlayGranted = true),
        )
    }

    @Test
    fun `returning user with everything granted reaches the app`() {
        assertTrue(
            isNavGateReachable(onboardingComplete = true, coreGranted = true, overlayGranted = true),
        )
    }
}
