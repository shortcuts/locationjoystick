package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.locationjoystick.app.MainActivity
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class RoutesSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var routeRepository: RouteRepository

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            routeRepository.insertRoute(
                Route(
                    id = "smoke-route-1",
                    name = "Smoke Test Route",
                    waypoints =
                        listOf(
                            Waypoint(id = "wp1", position = LatLng(48.8566, 2.3522), orderIndex = 0),
                            Waypoint(id = "wp2", position = LatLng(48.8600, 2.3600), orderIndex = 1),
                        ),
                    isLooping = false,
                    routeType = RouteType.STRAIGHT,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        composeRule.skipOnboarding()
        composeRule.navigateFromIdle("Routes")
    }

    @Test
    fun routes_screen_loads() {
        composeRule.onNodeWithText("Routes").assertIsDisplayed()
    }

    @Test
    fun seeded_route_is_visible() {
        composeRule.onNodeWithText("Smoke Test Route").assertIsDisplayed()
    }

    @Test
    fun replay_mode_dropdown_shows_all_options() {
        composeRule.onNodeWithContentDescription("Start route").performClick()
        composeRule.onNodeWithText("Walk route").assertIsDisplayed()
        composeRule.onNodeWithText("Return to location").assertIsDisplayed()
        composeRule.onNodeWithText("Loop").assertIsDisplayed()
        composeRule.onNodeWithText("Loop in reverse").assertIsDisplayed()
    }

    @Test
    fun route_overflow_menu_shows_edit_export_delete() {
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit").assertIsDisplayed()
        composeRule.onNodeWithText("Export").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }
}
