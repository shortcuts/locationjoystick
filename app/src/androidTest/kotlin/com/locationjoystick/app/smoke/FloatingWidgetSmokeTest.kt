package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
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
        composeRule.onNodeWithText("Menus").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun app_features_section_is_displayed() {
        composeRule.onNodeWithText("App features").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun app_features_shows_map_shortcut_feature() {
        composeRule.onNodeWithText("Map shortcut").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun app_features_shows_joystick_toggle_feature() {
        composeRule.onNodeWithText("Show/hide joystick").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun app_features_shows_joystick_lock_feature() {
        composeRule.onNodeWithText("Lock joystick").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun app_features_shows_routes_feature() {
        composeRule.onNodeWithText("Lists saved routes and starts replay.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun app_features_shows_favorites_feature() {
        composeRule.onNodeWithText("Teleport or walk to a saved location.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun app_features_shows_speed_cycle_feature() {
        composeRule.onNodeWithText("Cycles through all speed profiles with a single tap.").performScrollTo().assertIsDisplayed()
    }

    // Toggles persist at once, so each test clicks the row again to restore the value.
    @Test
    fun app_features_widget_toggle_no_crash() {
        composeRule.onNodeWithContentDescription("Routes on widget").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Routes on widget").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun app_features_map_toggle_no_crash() {
        composeRule.onNodeWithContentDescription("Favorites on map").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Favorites on map").performClick()
        composeRule.waitForIdle()
    }

    /**
     * Regression for a real state leak: the old version of [app_features_map_toggle_no_crash]
     * re-clicked "Favorites on map" a second time and hit an overlay instead of the row. Pins
     * that toggling a map feature off and on again here never leaks into the Map screen's FAB column.
     */
    @Test
    fun app_features_map_toggle_round_trip_leaves_favorites_fab_on_map() {
        composeRule.onNodeWithContentDescription("Favorites on map").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Favorites on map").performClick()
        composeRule.waitForIdle()
        Espresso.pressBack()
        composeRule.waitForIdle()
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.navigateFromIdle("Map")
        composeRule.onNodeWithContentDescription("Open favorites").assertIsDisplayed()
    }
}
