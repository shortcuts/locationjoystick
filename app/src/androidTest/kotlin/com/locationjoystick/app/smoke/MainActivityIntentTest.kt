package com.locationjoystick.app.smoke

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.locationjoystick.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class MainActivityIntentTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun intent_navigate_to_map_lands_on_map_screen() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.handleIntent(
                Intent(activity, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_NAVIGATE_TO_MAP, true),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Map").assertIsDisplayed()
    }

    @Test
    fun intent_navigate_to_route_creator_lands_on_creator() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.handleIntent(
                Intent(activity, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_NAVIGATE_TO_ROUTE_CREATOR, true),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("New route").assertIsDisplayed()
    }
}
