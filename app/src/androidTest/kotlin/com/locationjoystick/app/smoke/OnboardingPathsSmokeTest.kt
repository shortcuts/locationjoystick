package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.GuidedPath
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Inject

/** Walks the onboarding picker once per guided path, then opens every Settings hub card. */
@HiltAndroidTest
class OnboardingPathsSmokeTest : BaseSmokeTest() {
    @Inject lateinit var settings: SettingsRepository

    @Test
    fun default_path() = pickPathAndOpenSettings("Default", GuidedPath.DEFAULT)

    @Test
    fun pokemon_go_path() = pickPathAndOpenSettings("Pokémon Go", GuidedPath.POKEMON_GO)

    @Test
    fun pikmin_bloom_path() = pickPathAndOpenSettings("Pikmin Bloom", GuidedPath.PIKMIN_BLOOM)

    private fun pickPathAndOpenSettings(
        cardTitle: String,
        expected: GuidedPath,
    ) {
        // Clearing the flag reopens onboarding; a non-default stored path proves the pick overwrites it.
        runBlocking {
            settings.setGuidedPath(GuidedPath.entries.first { it != expected })
            settings.setOnboardingComplete(false)
        }
        // The nav host re-reads the flag only when it picks its start destination.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Set up locationjoystick").fetchSemanticsNodes().isNotEmpty() }
        if (composeRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onAllNodesWithText("Skip").onFirst().performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("How will you use it?").assertIsDisplayed()
        composeRule.onNodeWithText(cardTitle).performClick()
        composeRule.onNodeWithText("Start using locationjoystick").performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Set up locationjoystick").fetchSemanticsNodes().isEmpty() }
        composeRule.waitForIdleScreen()

        assertEquals(expected, runBlocking { settings.getGuidedPath().first() })

        composeRule.navigateFromIdle("Settings")
        listOf(
            "General",
            "Movement & GPS",
            "Randomness & realism",
            "Menus",
            "Screen helpers",
            "Favorites & Routes",
            "Roaming",
            "Capture",
        ).forEach { card ->
            val nodes = composeRule.onAllNodesWithText(card).filter(!hasAnyAncestor(hasTestTag("nav_drawer")))
            // Screen helpers only holds Tap to Walk and Reconnect accessory, so a path hiding both hides the card.
            val shown = card != "Screen helpers" || expected.showsTapToWalk || expected.showsReconnectAccessory
            assertEquals("$card card shown on $expected", shown, nodes.fetchSemanticsNodes().isNotEmpty())
            if (!shown) return@forEach
            nodes.onFirst().performClick()
            composeRule.waitForIdle()
            composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("Menus").assertIsDisplayed()
    }
}
