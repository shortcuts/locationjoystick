package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.model.LatLng
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class RouteCreatorSmokeTest : BaseSmokeTest() {
    @Inject lateinit var favoriteRepository: FavoriteRepository

    @Before
    override fun setup() {
        super.setup()
        runBlocking {
            favoriteRepository.addFavorite(
                id = "smoke-fav-1",
                name = "Smoke Favorite",
                position = LatLng(48.8566, 2.3522),
            )
        }
        composeRule.waitForIdleScreen()
        composeRule.navigateFromIdle("Routes")
        composeRule.onNodeWithContentDescription("Add route").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Draw on map").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun route_creator_screen_loads() {
        composeRule.onNodeWithContentDescription("Search location").assertIsDisplayed()
    }

    @Test
    fun navigate_back_from_creator() {
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Add route").assertIsDisplayed()
    }

    @Test
    fun route_creator_shows_search_button() {
        composeRule.onNodeWithContentDescription("Search location").assertIsDisplayed()
    }

    @Test
    fun route_creator_shows_undo_button() {
        composeRule.onNodeWithContentDescription("Undo last waypoint").assertIsDisplayed()
    }

    @Test
    fun route_creator_shows_favorites_button() {
        composeRule.onNodeWithContentDescription("Pick from favorites").assertIsDisplayed()
    }

    @Test
    fun route_creator_shows_spoof_toggle() {
        composeRule.onNodeWithContentDescription("Start location simulation").assertIsDisplayed()
    }

    @Test
    fun picking_favorite_offers_add_as_stop() {
        composeRule.onNodeWithContentDescription("Pick from favorites").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Smoke Favorite").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add as stop").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add as stop").assertDoesNotExist()
    }
}
