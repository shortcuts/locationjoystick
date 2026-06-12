package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test

@HiltAndroidTest
class IdleSmokeTest : BaseSmokeTest() {
    @Before
    override fun setup() {
        super.setup()
        composeRule.waitForIdleScreen()
    }

    @Test
    fun idle_screen_loads() {
        composeRule.onNodeWithText("locationjoystick").assertIsDisplayed()
    }

    @Test
    fun drawer_opens_and_shows_items() {
        composeRule.openDrawer()
        listOf("Map", "Routes", "Favorites", "Settings").forEach { label ->
            composeRule
                .onAllNodesWithText(label)
                .filterToOne(hasAnyAncestor(hasTestTag("nav_drawer")))
                .assertIsDisplayed()
        }
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
        composeRule.onNodeWithContentDescription("Start location simulation").assertIsDisplayed()
    }

    @Test
    fun navigate_to_routes_via_card() {
        composeRule.navigateFromIdle("Routes")
        composeRule.onNodeWithContentDescription("Add route").assertIsDisplayed()
    }

    @Test
    fun navigate_to_favorites_via_card() {
        composeRule.navigateFromIdle("Favorites")
        composeRule.onNodeWithContentDescription("Add favorite").assertIsDisplayed()
    }

    @Test
    fun navigate_to_settings_via_card() {
        composeRule.navigateFromIdle("Settings")
        composeRule.onNodeWithText("GPS Settings").assertIsDisplayed()
    }

    @Test
    fun navigate_to_map_via_drawer() {
        composeRule.navigateViaDrawer("Map")
        composeRule.onNodeWithContentDescription("Start location simulation").assertIsDisplayed()
    }

    @Test
    fun navigate_to_settings_via_drawer() {
        composeRule.navigateViaDrawer("Settings")
        composeRule.onNodeWithText("GPS Settings").assertIsDisplayed()
    }

    @Test
    fun navigate_to_routes_via_drawer() {
        composeRule.navigateViaDrawer("Routes")
        composeRule.onNodeWithContentDescription("Add route").assertIsDisplayed()
    }

    @Test
    fun navigate_to_favorites_via_drawer() {
        composeRule.navigateViaDrawer("Favorites")
        composeRule.onNodeWithContentDescription("Add favorite").assertIsDisplayed()
    }
}
