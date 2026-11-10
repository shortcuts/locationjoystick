package com.locationjoystick.feature.settings.impl

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRoamingUnitsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(speedUnit: SpeedUnit) {
        composeRule.setContent {
            SettingsRoamingSubScreen(
                screenState = SettingsScreenState.Loaded(SettingsUiState(speedUnit = speedUnit)),
                roamingDefaults = RoamingDefaults(),
                onNavigateBack = {},
                isSpoofing = false,
                onToggleSpoofing = {},
                onAction = {},
                bottomBar = {},
                snackbarHost = {},
            )
        }
    }

    @Test
    fun `km per hour shows metre distances even when planting is visible`() {
        setScreen(SpeedUnit.KMH)

        composeRule.onNodeWithText("Radius (m)").assertIsDisplayed()
        composeRule.onNodeWithText("Route distance (m)").assertIsDisplayed()
    }

    @Test
    fun `miles per hour shows mile distances`() {
        setScreen(SpeedUnit.MPH)

        composeRule.onNodeWithText("Radius (mi)").assertIsDisplayed()
        composeRule.onNodeWithText("Route distance (mi)").assertIsDisplayed()
    }

    @Test
    fun `each speed row names its mode`() {
        setScreen(SpeedUnit.KMH)

        composeRule.onNodeWithText("Walk around the block speed").assertIsDisplayed()
        composeRule.onNodeWithText("Planting speed").performScrollTo().assertIsDisplayed()
    }
}
