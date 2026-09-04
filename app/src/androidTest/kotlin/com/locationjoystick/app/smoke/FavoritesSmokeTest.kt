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
class FavoritesSmokeTest : BaseSmokeTest() {
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
        composeRule.navigateFromIdle("Favorites")
    }

    @Test
    fun favorites_screen_loads() {
        composeRule.onNodeWithContentDescription("Add favorite").assertIsDisplayed()
    }

    @Test
    fun seeded_favorite_is_visible() {
        composeRule.onNodeWithText("Smoke Favorite").assertIsDisplayed()
    }

    @Test
    fun navigate_to_map_picker() {
        composeRule.onNodeWithContentDescription("Add favorite").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pick on map").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Search location").assertIsDisplayed()
    }

    @Test
    fun add_dropdown_shows_all_three_options() {
        composeRule.onNodeWithContentDescription("Add favorite").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pick on map").assertIsDisplayed()
        composeRule.onNodeWithText("Enter coordinates").assertIsDisplayed()
        composeRule.onNodeWithText("Use current location").assertIsDisplayed()
    }

    @Test
    fun favorite_item_menu_shows_edit_and_delete() {
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun navigate_back_from_map_picker() {
        composeRule.onNodeWithContentDescription("Add favorite").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pick on map").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Search location").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Add favorite").assertIsDisplayed()
    }

    @Test
    fun from_coordinates_dialog_opens() {
        composeRule.onNodeWithContentDescription("Add favorite").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Enter coordinates").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Latitude", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Longitude", substring = true).assertIsDisplayed()
    }
}
