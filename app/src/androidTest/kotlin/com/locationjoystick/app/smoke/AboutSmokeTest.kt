package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.locationjoystick.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class AboutSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
        composeRule.skipOnboarding()
        composeRule.navigateFromIdle("About")
    }

    @Test
    fun about_screen_loads() {
        composeRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun navigate_back_from_about() {
        composeRule.onNodeWithContentDescription("Open navigation menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("locationjoystick").assertIsDisplayed()
    }

    @Test
    fun about_shows_app_name_and_version() {
        composeRule.onNodeWithText("locationjoystick").assertIsDisplayed()
        composeRule.onNodeWithText("v", substring = true).assertIsDisplayed()
    }

    @Test
    fun about_shows_credits_section() {
        composeRule.onNodeWithText("Credits").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("MapLibre Android SDK", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun about_shows_license_section() {
        composeRule.onNodeWithText("License").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("MIT").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun about_shows_links_section() {
        composeRule.onNodeWithText("Links").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("GitHub").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Report a bug").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun about_shows_privacy_section() {
        composeRule.onNodeWithText("Privacy").performScrollTo().assertIsDisplayed()
    }
}
