package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test

@HiltAndroidTest
class RouteCreatorSmokeTest : BaseSmokeTest() {
    @Before
    override fun setup() {
        super.setup()
        composeRule.waitForIdleScreen()
        composeRule.navigateFromIdle("Routes")
        composeRule.onNodeWithContentDescription("Add route").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Draw on map").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun route_creator_screen_loads() {
        composeRule.onNodeWithContentDescription("Search location").assertIsDisplayed()
    }

    @Test
    fun navigate_back_from_creator() {
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Add route").assertIsDisplayed()
    }

    @Test
    fun route_creator_shows_search_button() {
        composeRule.onNodeWithContentDescription("Search location").assertIsDisplayed()
    }

    @Test
    fun route_creator_shows_undo_button() {
        composeRule.onNodeWithContentDescription("Undo last waypoint").assertIsDisplayed()
    }

    @Test
    fun route_creator_shows_favorites_button() {
        composeRule.onNodeWithContentDescription("Pick from favorites").assertIsDisplayed()
    }

    @Test
    fun route_creator_shows_spoof_toggle() {
        composeRule.onNodeWithContentDescription("Start location simulation").assertIsDisplayed()
    }
}
