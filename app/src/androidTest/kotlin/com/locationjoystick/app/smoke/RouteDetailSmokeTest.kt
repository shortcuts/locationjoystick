package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
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
class RouteDetailSmokeTest : BaseSmokeTest() {
    @Inject lateinit var routeRepository: RouteRepository

    @Before
    override fun setup() {
        super.setup()
        runBlocking {
            routeRepository.insertRoute(
                Route(
                    id = "smoke-route-detail-1",
                    name = "Detail Smoke Route",
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
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Detail Smoke Route", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Detail Smoke Route", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun route_detail_screen_loads() {
        composeRule.onNodeWithText("Detail Smoke Route", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun navigate_back_from_detail() {
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Add route").assertIsDisplayed()
    }

    @Test
    fun route_detail_shows_delete_button() {
        composeRule.onAllNodesWithContentDescription("Remove waypoint")[0].assertIsDisplayed()
    }

    @Test
    fun route_detail_shows_route_name_field() {
        composeRule.onNodeWithText("Route name", substring = true).assertIsDisplayed()
    }

    @Test
    fun route_detail_shows_waypoint_list() {
        composeRule.onNodeWithText("Waypoint 1", substring = true).assertIsDisplayed()
    }
}
