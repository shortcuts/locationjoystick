package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
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
    fun settings_hub_order_is_general_gps_menus_favorites_roaming_capture() {
        val labels = listOf("General", "Movement & GPS", "Menus", "Favorites & Routes", "Roaming", "Capture")
        val tops =
            labels.map {
                // The drawer also has a "Capture" tab; the hub card comes first.
                composeRule
                    .onAllNodesWithText(it)
                    .onFirst()
                    .fetchSemanticsNode()
                    .boundsInRoot.top
            }
        assertEquals("hub card order", tops.sorted(), tops)
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
        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun settings_shows_speed_profiles_section() {
        composeRule.onNodeWithText("Movement & GPS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Speed profiles").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_gps_jitter_section() {
        composeRule.onNodeWithText("Movement & GPS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Location randomness").performScrollTo().assertIsDisplayed()
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
        composeRule.onNodeWithText("App features").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_general_section() {
        composeRule.onNodeWithText("General").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_location_memory_section() {
        composeRule.onNodeWithText("Movement & GPS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Location memory").performScrollTo().assertIsDisplayed()
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

    @Test
    fun settings_shows_planting_defaults() {
        composeRule.onNodeWithText("Roaming").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Starting radius (m)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_capture_section() {
        composeRule.onAllNodesWithText("Capture").onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Collects map links", substring = true).performScrollTo().assertIsDisplayed()
    }
}
