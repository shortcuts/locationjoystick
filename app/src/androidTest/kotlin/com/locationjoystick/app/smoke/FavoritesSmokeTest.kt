package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

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
}
