package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/** GitHub flavor only. The test never taps Update now: that downloads and installs a release. */
@HiltAndroidTest
class AppUpdateSmokeTest : BaseSmokeTest() {
    @Test
    fun app_update_screen_shows_the_update_button() {
        composeRule.waitForIdleScreen()
        composeRule.navigateFromIdle("Settings")
        composeRule.onNodeWithText("App update").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Update now").assertIsDisplayed()
    }
}
