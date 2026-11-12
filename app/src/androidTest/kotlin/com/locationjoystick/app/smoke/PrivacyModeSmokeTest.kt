package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** GitHub flavor only: the Privacy Mode card does not exist in the Play build. */
@HiltAndroidTest
class PrivacyModeSmokeTest : BaseSmokeTest() {
    @Before
    override fun setup() {
        super.setup()
        composeRule.waitForIdleScreen()
        composeRule.navigateFromIdle("Settings")
        composeRule.onNodeWithText("Privacy Mode").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun switch_off_by_default_then_disclosure_then_controls() {
        composeRule.onNode(isToggleable()).assertIsOff()
        assertTrue(composeRule.onAllNodesWithText("Clone name").fetchSemanticsNodes().isEmpty())

        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNodeWithText("I understand").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNode(isToggleable()).assertIsOff()

        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNodeWithText("I understand").performClick()
        composeRule.onNode(isToggleable()).assertIsOn()
        composeRule.onNodeWithText("Clone name").assertIsDisplayed()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
    }
}
