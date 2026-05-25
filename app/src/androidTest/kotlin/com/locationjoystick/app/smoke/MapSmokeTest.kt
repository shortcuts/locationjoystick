package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Test

class MapSmokeTest : BaseSmokeTest() {
    @Before
    override fun setup() {
        super.setup()
        composeRule.waitForIdleScreen()
        composeRule.navigateFromIdle("Map")
    }

    @Test
    fun map_screen_loads() {
        composeRule.onNodeWithText("Map").assertIsDisplayed()
    }

    @Test
    fun map_screen_opens_drawer() {
        composeRule.onNodeWithContentDescription("Open navigation menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Routes").assertIsDisplayed()
    }

    @Test
    fun map_recenter_fab_is_displayed() {
        composeRule.onNodeWithContentDescription("Re-center").assertIsDisplayed()
    }
}
