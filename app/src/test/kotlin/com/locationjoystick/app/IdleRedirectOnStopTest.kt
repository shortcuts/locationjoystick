package com.locationjoystick.app

import com.locationjoystick.feature.favorites.api.FAVORITES_ROUTE
import com.locationjoystick.feature.favorites.api.MAP_PICKER_ROUTE
import com.locationjoystick.feature.map.api.CAPTURE_ROUTE
import com.locationjoystick.feature.map.api.MAP_ROUTE
import com.locationjoystick.feature.onboarding.api.ONBOARDING_ROUTE
import com.locationjoystick.feature.routes.api.ROUTES_ROUTE
import com.locationjoystick.feature.routes.api.ROUTE_CREATOR_ROUTE
import com.locationjoystick.feature.routes.api.ROUTE_PASTE_CREATOR_ROUTE
import com.locationjoystick.feature.settings.api.SETTINGS_ROUTE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleRedirectOnStopTest {
    @Test
    fun `idle onboarding and settings stay put`() {
        assertTrue(shouldSkipIdleRedirect(IDLE_ROUTE))
        assertTrue(shouldSkipIdleRedirect(ONBOARDING_ROUTE))
        assertTrue(shouldSkipIdleRedirect(SETTINGS_ROUTE))
    }

    @Test
    fun `paste coordinates stays put so copying from another app does not dismiss it`() {
        assertTrue(shouldSkipIdleRedirect(ROUTE_PASTE_CREATOR_ROUTE))
        assertTrue(shouldSkipIdleRedirect(FAVORITES_ROUTE))
        assertTrue(shouldSkipIdleRedirect(CAPTURE_ROUTE))
    }

    @Test
    fun `map routes and creator still return to idle`() {
        assertFalse(shouldSkipIdleRedirect(MAP_ROUTE))
        assertFalse(shouldSkipIdleRedirect(ROUTES_ROUTE))
        assertFalse(shouldSkipIdleRedirect(MAP_PICKER_ROUTE))
        assertFalse(shouldSkipIdleRedirect("$ROUTE_CREATOR_ROUTE/STRAIGHT"))
        assertFalse(shouldSkipIdleRedirect(null))
    }
}
