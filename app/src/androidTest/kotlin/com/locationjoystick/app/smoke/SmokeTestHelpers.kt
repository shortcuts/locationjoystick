package com.locationjoystick.app.smoke

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.locationjoystick.app.MainActivity

typealias SmokeRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

fun SmokeRule.waitForIdleScreen() {
    activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    waitForIdle()
    // Defensive: onboarding should not appear because HiltTestRunner pre-grants all
    // permissions before the activity starts. If it does appear, something is wrong
    // with permission setup.
    if (onAllNodesWithText("Start using locationjoystick").fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithText("Start using locationjoystick").performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("Start using locationjoystick").fetchSemanticsNodes().isEmpty()
        }
    }
    waitForIdle()
}

fun SmokeRule.openDrawer() {
    onNodeWithContentDescription("Open navigation menu").performClick()
    waitForIdle()
}

fun SmokeRule.navigateViaDrawer(label: String) {
    openDrawer()
    onNodeWithText(label).performClick()
    waitForIdle()
}

fun SmokeRule.navigateFromIdle(cardLabel: String) {
    onNodeWithText(cardLabel).performClick()
    waitForIdle()
}
