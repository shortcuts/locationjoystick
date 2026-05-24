package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.locationjoystick.app.MainActivity
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.model.LatLng
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class FavoritesSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var favoriteRepository: FavoriteRepository

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            favoriteRepository.addFavorite(
                id = "smoke-fav-1",
                name = "Smoke Favorite",
                position = LatLng(48.8566, 2.3522),
            )
        }
        composeRule.skipOnboarding()
        composeRule.navigateFromIdle("Favorites")
    }

    @Test
    fun favorites_screen_loads() {
        composeRule.onNodeWithText("Favorites").assertIsDisplayed()
    }

    @Test
    fun seeded_favorite_is_visible() {
        composeRule.onNodeWithText("Smoke Favorite").assertIsDisplayed()
    }

    @Test
    fun navigate_to_map_picker() {
        composeRule.onNodeWithContentDescription("Add options").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("from map").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pick Location").assertIsDisplayed()
    }

    @Test
    fun add_dropdown_shows_all_three_options() {
        composeRule.onNodeWithContentDescription("Add options").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("from map").assertIsDisplayed()
        composeRule.onNodeWithText("from coordinates").assertIsDisplayed()
        composeRule.onNodeWithText("from current location").assertIsDisplayed()
    }

    @Test
    fun favorite_item_menu_shows_edit_and_delete() {
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }
}
