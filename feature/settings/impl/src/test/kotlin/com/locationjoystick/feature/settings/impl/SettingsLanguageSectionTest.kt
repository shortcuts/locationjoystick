package com.locationjoystick.feature.settings.impl

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsLanguageSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `language section shows a header and the current choice`() {
        composeRule.setContent { LanguageSection(languageTag = null, onAction = {}) }

        composeRule.onNodeWithText("Language").assertIsDisplayed()
        composeRule.onNodeWithText("System default").assertIsDisplayed()
    }

    @Test
    fun `picking a language emits SetLanguage`() {
        val actions = mutableListOf<SettingsAction>()
        composeRule.setContent { LanguageSection(languageTag = null, onAction = { actions += it }) }

        composeRule.onNodeWithContentDescription("Change language").performClick()
        composeRule.onNodeWithText("EN").performClick()

        assertEquals(listOf<SettingsAction>(SettingsAction.SetLanguage("en")), actions)
    }
}
