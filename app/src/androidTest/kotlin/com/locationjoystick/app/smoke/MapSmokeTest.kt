package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test

@HiltAndroidTest
class MapSmokeTest : BaseSmokeTest() {
    @Before
    override fun setup() {
        super.setup()
        composeRule.waitForIdleScreen()
        composeRule.navigateFromIdle("Map")
    }

    @Test
    fun map_screen_loads() {
        composeRule.onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun map_screen_opens_drawer() {
        composeRule.onNodeWithContentDescription("Open navigation menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Routes").assertIsDisplayed()
    }

    @Test
    fun map_open_favorites_fab_is_displayed() {
        composeRule.onNodeWithContentDescription("Open favorites").assertIsDisplayed()
    }

    @Test
    fun map_open_routes_fab_is_displayed() {
        composeRule.onNodeWithContentDescription("Open routes").assertIsDisplayed()
    }

    @Test
    fun map_start_roaming_fab_is_displayed() {
        composeRule.onNodeWithContentDescription("Start roaming").assertIsDisplayed()
    }

    @Test
    fun map_search_location_fab_is_displayed() {
        composeRule.onNodeWithContentDescription("Search location").assertIsDisplayed()
    }
}
