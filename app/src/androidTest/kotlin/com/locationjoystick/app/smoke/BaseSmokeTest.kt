package com.locationjoystick.app.smoke

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.locationjoystick.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule

@HiltAndroidTest
abstract class BaseSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    open fun setup() {
        hiltRule.inject()
        // The app keeps onboarding done for the rest of the run, so only the first test drives it.
        if (!onboarded) {
            composeRule.completeOnboarding()
            onboarded = true
        }
        // The onboarding flag loads from DataStore after the first frame, so a later test opens on onboarding first.
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Set up locationjoystick").fetchSemanticsNodes().isEmpty() }
    }

    private companion object {
        var onboarded = false
    }
}
