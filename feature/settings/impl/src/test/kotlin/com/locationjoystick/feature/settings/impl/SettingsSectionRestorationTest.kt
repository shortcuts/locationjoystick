package com.locationjoystick.feature.settings.impl

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsSectionRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    @Test
    fun `open section survives activity recreation`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            SettingsScreen(screenState = SettingsScreenState.Loaded(SettingsUiState()), onAction = {})
        }
        val captureCard = string(R.string.settings_hub_capture)
        composeRule.onAllNodesWithText(string(R.string.settings_hub_roaming))[0].performScrollTo().performClick()
        composeRule.onAllNodesWithText(captureCard).assertCountEquals(0)

        restoration.emulateSavedInstanceStateRestore()
        composeRule.onRoot().printToLog("DBG")

        composeRule.onAllNodesWithText(captureCard).assertCountEquals(0)
    }
}
