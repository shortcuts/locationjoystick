package com.locationjoystick.app.smoke

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.locationjoystick.app.MainActivity
import com.locationjoystick.core.common.constants.AppConstants
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
                    .putExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_MAP, true),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Start location simulation").assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription("Search location").assertIsDisplayed()
    }

    @Test
    fun intent_navigate_to_favorites_lands_on_favorites_screen() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.handleIntent(
                Intent(activity, MainActivity::class.java)
                    .putExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_FAVORITES, true),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Add favorite").assertIsDisplayed()
    }

    @Test
    fun intent_navigate_to_routes_lands_on_routes_screen() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.handleIntent(
                Intent(activity, MainActivity::class.java)
                    .putExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_ROUTES, true),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Add route").assertIsDisplayed()
    }

    @Test
    fun existing_navigate_to_map_still_works_after_constant_migration() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.handleIntent(
                Intent(activity, MainActivity::class.java)
                    .putExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_MAP, true),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Start location simulation").assertIsDisplayed()
    }
}
