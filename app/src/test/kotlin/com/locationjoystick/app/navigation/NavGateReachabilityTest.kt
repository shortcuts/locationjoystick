package com.locationjoystick.app.navigation

import com.locationjoystick.app.IDLE_ROUTE
import com.locationjoystick.feature.onboarding.api.ONBOARDING_ROUTE
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

    @Test
    fun `first run that starts on home is sent back to onboarding`() {
        assertTrue(shouldReturnToOnboarding(IDLE_ROUTE, onboardingComplete = false))
    }

    @Test
    fun `returning user that starts on home stays on home`() {
        assertFalse(shouldReturnToOnboarding(IDLE_ROUTE, onboardingComplete = true))
    }

    @Test
    fun `first run that starts on onboarding is not redirected again`() {
        assertFalse(shouldReturnToOnboarding(ONBOARDING_ROUTE, onboardingComplete = false))
    }
}
