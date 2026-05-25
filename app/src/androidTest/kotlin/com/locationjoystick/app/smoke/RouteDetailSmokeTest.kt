package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import javax.inject.Inject

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
        composeRule.onNodeWithText("Detail Smoke Route").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun route_detail_screen_loads() {
        composeRule.onNodeWithText("Detail Smoke Route").assertIsDisplayed()
    }

    @Test
    fun navigate_back_from_detail() {
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Routes").assertIsDisplayed()
    }

    @Test
    fun route_detail_shows_delete_button() {
        composeRule.onNodeWithContentDescription("Delete route").assertIsDisplayed()
    }

    @Test
    fun route_detail_shows_route_name_field() {
        composeRule.onNodeWithText("Route name", substring = true).assertIsDisplayed()
    }

    @Test
    fun route_detail_shows_waypoint_list() {
        composeRule.onNodeWithText("Waypoints", substring = true).assertIsDisplayed()
    }
}
