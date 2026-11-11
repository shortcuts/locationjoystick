package com.locationjoystick.feature.settings.impl

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.feature.settings.api.SettingsExtension
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeExtension : SettingsExtension {
    override val id = "fake"
    override val icon: ImageVector = LjIcons.Link

    @Composable
    override fun title() = "Fake feature"

    @Composable
    override fun description() = "Fake description"

    @Composable
    override fun Screen(onBack: () -> Unit) {
        Text("Fake screen")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsExtensionHubTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun content(extensions: List<SettingsExtension>) =
        composeRule.setContent {
            SettingsScreen(
                screenState = SettingsScreenState.Loaded(SettingsUiState()),
                onAction = {},
                extensions = extensions,
            )
        }

    @Test
    fun `empty extension list shows no extension card`() {
        content(emptyList())

        composeRule.onAllNodesWithText("Fake feature").assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.settings_hub_capture)).assertCountEquals(1)
    }

    @Test
    fun `extension adds a card that opens its screen and back returns to the hub`() {
        content(listOf(FakeExtension()))

        composeRule
            .onNodeWithText("Fake feature")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Fake screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("Fake feature").assertCountEquals(0)

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("Fake feature").assertExists()
        composeRule.onAllNodesWithText("Fake screen").assertCountEquals(0)
    }

    @Test
    fun `saved id of a missing extension falls back to the hub`() {
        val restoration = StateRestorationTester(composeRule)
        var extensions = listOf<SettingsExtension>(FakeExtension())
        restoration.setContent {
            SettingsScreen(
                screenState = SettingsScreenState.Loaded(SettingsUiState()),
                onAction = {},
                extensions = extensions,
            )
        }
        composeRule.onNodeWithText("Fake feature").performScrollTo().performClick()
        composeRule.onNodeWithText("Fake screen").assertIsDisplayed()

        extensions = emptyList()
        restoration.emulateSavedInstanceStateRestore()

        composeRule.onAllNodesWithText("Fake screen").assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.settings_hub_capture)).assertCountEquals(1)
    }
}
