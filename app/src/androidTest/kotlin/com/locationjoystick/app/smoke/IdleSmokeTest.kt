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
class IdleSmokeTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
        composeRule.skipOnboarding()
    }

    @Test
    fun idle_screen_loads() {
        composeRule.onNodeWithText("locationjoystick").assertIsDisplayed()
    }

    @Test
    fun drawer_opens_and_shows_items() {
        composeRule.openDrawer()
        composeRule.onNodeWithText("Map").assertIsDisplayed()
        composeRule.onNodeWithText("Routes").assertIsDisplayed()
        composeRule.onNodeWithText("Favorites").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun drawer_closes_via_close_button() {
        composeRule.openDrawer()
        composeRule.onNodeWithContentDescription("Close menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("locationjoystick").assertIsDisplayed()
    }

    @Test
    fun navigate_to_map_via_card() {
        composeRule.navigateFromIdle("Map")
        composeRule.onNodeWithText("Map").assertIsDisplayed()
    }

    @Test
    fun navigate_to_routes_via_card() {
        composeRule.navigateFromIdle("Routes")
        composeRule.onNodeWithText("Routes").assertIsDisplayed()
    }

    @Test
    fun navigate_to_favorites_via_card() {
        composeRule.navigateFromIdle("Favorites")
        composeRule.onNodeWithText("Favorites").assertIsDisplayed()
    }

    @Test
    fun navigate_to_settings_via_card() {
        composeRule.navigateFromIdle("Settings")
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun navigate_to_about_via_card() {
        composeRule.navigateFromIdle("About")
        composeRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun navigate_to_map_via_drawer() {
        composeRule.navigateViaDrawer("Map")
        composeRule.onNodeWithText("Map").assertIsDisplayed()
    }

    @Test
    fun navigate_to_settings_via_drawer() {
        composeRule.navigateViaDrawer("Settings")
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }
}
