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
        composeRule.onNodeWithText("GPS Settings").assertIsDisplayed()
    }

    @Test
    fun speed_unit_toggle_no_crash() {
        composeRule.onNodeWithText("GPS Settings").performClick()
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
        composeRule.onNodeWithText("GPS Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Speed Profiles").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_gps_jitter_section() {
        composeRule.onNodeWithText("GPS Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("GPS Jitter").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_gps_realism_section() {
        composeRule.onNodeWithText("GPS Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("GPS Realism").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_map_section() {
        composeRule.onNodeWithText("GPS Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Remember last location").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_floating_widget_section() {
        composeRule.onNodeWithText("Menus").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Floating Widget").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_roaming_section() {
        composeRule.onNodeWithText("Favorites & Routes").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Roaming").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_export_icon_is_displayed() {
        composeRule.onNodeWithContentDescription("Export").assertIsDisplayed()
    }

    @Test
    fun settings_import_icon_is_displayed() {
        composeRule.onNodeWithContentDescription("Import").assertIsDisplayed()
    }

    @Test
    fun export_dropdown_shows_options() {
        composeRule.onNodeWithContentDescription("Export").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Export via QR code").assertIsDisplayed()
        composeRule.onNodeWithText("Export settings").assertIsDisplayed()
    }

    @Test
    fun import_dropdown_shows_options() {
        composeRule.onNodeWithContentDescription("Import").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Import from QR code").assertIsDisplayed()
        composeRule.onNodeWithText("Import from file").assertIsDisplayed()
        composeRule.onNodeWithText("Import from GPS Joystick").assertIsDisplayed()
        composeRule.onNodeWithText("Import from YAMLA").assertIsDisplayed()
    }
}
