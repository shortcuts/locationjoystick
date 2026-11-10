package com.locationjoystick.feature.settings.impl

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsGpsSplitTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun openFromHub(card: String) {
        composeRule.setContent { SettingsScreen(screenState = SettingsScreenState.Loaded(SettingsUiState()), onAction = {}) }
        composeRule.onNodeWithText(card).performClick()
    }

    @Test
    fun `movement screen holds speeds only`() {
        openFromHub("Movement & GPS")

        composeRule.onNodeWithText("Speed profiles").assertIsDisplayed()
        composeRule.onAllNodesWithText("Location randomness").assertCountEquals(0)
        composeRule.onAllNodesWithText("GPS Realism").assertCountEquals(0)
        composeRule.onAllNodesWithText("Location memory").assertCountEquals(0)
    }

    @Test
    fun `realism screen holds randomness, realism and location memory`() {
        openFromHub("Randomness & realism")

        composeRule.onNodeWithText("Location randomness").assertIsDisplayed()
        composeRule.onAllNodesWithText("Speed profiles").assertCountEquals(0)
    }
}
