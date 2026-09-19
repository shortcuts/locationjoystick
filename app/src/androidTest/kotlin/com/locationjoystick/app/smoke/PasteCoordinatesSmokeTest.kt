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
class PasteCoordinatesSmokeTest : BaseSmokeTest() {
    @Before
    override fun setup() {
        super.setup()
        composeRule.waitForIdleScreen()
        composeRule.navigateFromIdle("Routes")
        composeRule.onNodeWithContentDescription("Add route").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Paste coordinates").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun paste_coordinates_screen_loads() {
        composeRule.onNodeWithText("Coordinates").assertIsDisplayed()
        composeRule.onNodeWithText("Load points").assertIsDisplayed()
        composeRule.onNodeWithText("Planting mode").assertIsDisplayed()
    }

    @Test
    fun navigate_back_from_paste_coordinates() {
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Add route").assertIsDisplayed()
    }
}
