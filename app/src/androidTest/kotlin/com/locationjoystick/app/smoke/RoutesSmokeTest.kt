package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class RoutesSmokeTest : BaseSmokeTest() {
    @Inject lateinit var routeRepository: RouteRepository

    @Before
    override fun setup() {
        super.setup()
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
        composeRule.waitForIdleScreen()
        composeRule.navigateFromIdle("Routes")
    }

    @Test
    fun routes_screen_loads() {
        composeRule.onNodeWithContentDescription("Add route").assertIsDisplayed()
    }

    @Test
    fun seeded_route_is_visible() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Smoke Test Route", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Smoke Test Route", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun start_route_dialog_shows_all_options() {
        composeRule.onNodeWithContentDescription("Start route").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Loop").assertIsDisplayed()
        composeRule.onNodeWithText("Reverse").assertIsDisplayed()
        composeRule.onNodeWithText("Return to location").assertIsDisplayed()
        composeRule.onNodeWithText("Walk and start").assertIsDisplayed()
        composeRule.onNodeWithText("Teleport and start").assertIsDisplayed()
    }

    @Test
    fun route_overflow_menu_shows_edit_export_delete() {
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit").assertIsDisplayed()
        composeRule.onNodeWithText("Export").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }
}
