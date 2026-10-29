package com.locationjoystick.feature.settings.impl

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GpsRealismSectionResetButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setSection(hasAltitudeOverride: Boolean) {
        composeRule.setContent {
            GpsRealismSection(
                uiState = SettingsUiState(isLoading = false, hasAltitudeOverride = hasAltitudeOverride),
                isMph = false,
                onAction = {},
            )
        }
    }

    @Test
    fun `reset elevation override button is hidden when no override is active`() {
        setSection(hasAltitudeOverride = false)

        composeRule.onAllNodesWithText("Reset elevation override").assertCountEquals(0)
    }

    @Test
    fun `reset elevation override button is shown when an override is active`() {
        setSection(hasAltitudeOverride = true)

        composeRule.onNodeWithText("Reset elevation override").assertIsDisplayed()
    }
}
