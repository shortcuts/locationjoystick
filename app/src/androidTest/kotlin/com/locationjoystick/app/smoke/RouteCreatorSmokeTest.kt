package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.locationjoystick.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class RouteCreatorSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
        composeRule.skipOnboarding()
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
        composeRule.onNodeWithContentDescription("Open navigation menu").performClick()
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
}
