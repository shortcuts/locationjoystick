package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.locationjoystick.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class SettingsSmokeTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

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
}
