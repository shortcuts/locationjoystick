package com.locationjoystick.app.smoke

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.locationjoystick.app.MainActivity

typealias SmokeRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

fun SmokeRule.skipOnboarding() {
    onNodeWithText("Start using locationjoystick").performClick()
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
