package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.locationjoystick.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class SettingsSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
        composeRule.skipOnboarding()
        composeRule.navigateFromIdle("Settings")
    }

    @Test
    fun settings_screen_loads() {
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun speed_unit_toggle_no_crash() {
        composeRule.onNodeWithText("mph").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("km/h").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun export_button_no_crash() {
        composeRule.onNodeWithText("Export").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun settings_shows_speed_profiles_section() {
        composeRule.onNodeWithText("Speed Profiles").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_gps_jitter_section() {
        composeRule.onNodeWithText("GPS Jitter").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_gps_realism_section() {
        composeRule.onNodeWithText("GPS Realism").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_map_section() {
        composeRule.onNodeWithText("Remember last location").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_floating_widget_section() {
        composeRule.onNodeWithText("Floating Widget").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_roaming_section() {
        composeRule.onNodeWithText("Roaming").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_data_management_section() {
        composeRule.onNodeWithText("Data Management").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_shows_import_button() {
        composeRule.onNodeWithText("Import Settings").performScrollTo().assertIsDisplayed()
    }
}
