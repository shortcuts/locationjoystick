package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test

@HiltAndroidTest
class SettingsSmokeTest : BaseSmokeTest() {
    @Before
    override fun setup() {
        super.setup()
        composeRule.waitForIdleScreen()
        composeRule.navigateFromIdle("Settings")
    }

    @Test
    fun settings_screen_loads() {
        composeRule.onNodeWithText("Movement & GPS").assertIsDisplayed()
    }

    @Test
    fun speed_unit_toggle_no_crash() {
        composeRule.onNodeWithText("Movement & GPS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("mph").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("km/h").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun export_button_no_crash() {
        composeRule.onNodeWithContentDescription("Export").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun settings_shows_speed_profiles_section() {
        composeRule.onNodeWithText("Movement & GPS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Speed Profiles").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_gps_jitter_section() {
        composeRule.onNodeWithText("Movement & GPS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Location Randomness").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_gps_realism_section() {
        composeRule.onNodeWithText("Movement & GPS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("GPS Realism").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_map_section() {
        composeRule.onNodeWithText("Movement & GPS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Remember last location").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_app_features_section() {
        composeRule.onNodeWithText("Menus").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("App Features").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_location_memory_section() {
        composeRule.onNodeWithText("Movement & GPS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Location Memory").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_favorites_section() {
        composeRule.onNodeWithText("Favorites & Routes").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Options for the favorites list.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_routes_section() {
        composeRule.onNodeWithText("Favorites & Routes").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Options for the routes list.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_roaming_section() {
        composeRule.onNodeWithText("Roaming").performClick()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText(
                "Default settings used when starting a roaming session from the map.",
            ).performScrollTo()
            .assertIsDisplayed()
    }
}
