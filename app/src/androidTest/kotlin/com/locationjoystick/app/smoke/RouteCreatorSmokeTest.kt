package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import org.junit.Before
import org.junit.Test

class RouteCreatorSmokeTest : BaseSmokeTest() {
    @Before
    override fun setup() {
        super.setup()
        composeRule.waitForIdleScreen()
        composeRule.navigateFromIdle("Routes")
        composeRule.onNodeWithContentDescription("New route").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun route_creator_screen_loads() {
        composeRule.onNodeWithText("New route").assertIsDisplayed()
    }

    @Test
    fun navigate_back_from_creator() {
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Routes").assertIsDisplayed()
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
    fun route_creator_shows_save_route_button() {
        composeRule.onNodeWithText("Save Route").assertIsDisplayed()
    }

    @Test
    fun route_creator_shows_route_type() {
        composeRule.onNodeWithText("Straight", substring = true).assertIsDisplayed()
    }
}
