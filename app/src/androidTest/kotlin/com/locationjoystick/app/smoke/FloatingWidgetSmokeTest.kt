package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test

@HiltAndroidTest
class FloatingWidgetSmokeTest : BaseSmokeTest() {
    @Before
    override fun setup() {
        super.setup()
        composeRule.waitForIdleScreen()
        composeRule.navigateFromIdle("Settings")
    }

    @Test
    fun widget_section_is_displayed() {
        composeRule.onNodeWithText("Floating Widget").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun widget_shows_map_shortcut_feature() {
        composeRule.onNodeWithText("Map shortcut").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun widget_shows_joystick_toggle_feature() {
        composeRule.onNodeWithText("Show/hide joystick").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun widget_shows_joystick_lock_feature() {
        composeRule.onNodeWithText("Lock joystick position").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun widget_shows_routes_picker_feature() {
        composeRule.onNodeWithText("Routes picker").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun widget_shows_favorites_picker_feature() {
        composeRule.onNodeWithText("Favorites picker").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun widget_shows_speed_cycle_feature() {
        composeRule.onNodeWithText("Speed cycle").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun widget_feature_toggle_no_crash() {
        composeRule.onNodeWithText("Routes picker").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Routes picker").performClick()
        composeRule.waitForIdle()
    }
}
