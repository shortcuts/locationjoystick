package com.locationjoystick.feature.settings.impl

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HotItemTreeSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val tree =
        HotItemTree(
            allIds = setOf("a"),
            byCountry = mapOf("Japan" to mapOf("Tokyo" to listOf(HotItemEntry("a", "Shibuya")))),
        )

    private fun setSection() {
        composeRule.setContent {
            HotItemTreeSection(
                headerLabel = "Hot locations",
                tree = tree,
                selectedIds = emptySet(),
                onSelectionChange = {},
            )
        }
    }

    @Test
    fun `tree starts collapsed`() {
        setSection()
        composeRule.onAllNodesWithText("Japan").assertCountEquals(0)
        composeRule.onNodeWithText("Hot locations").assertIsDisplayed()
    }

    @Test
    fun `header toggle expands the tree`() {
        setSection()
        composeRule.onNodeWithContentDescription("Expand").performClick()
        composeRule.onNodeWithContentDescription("Collapse").assertIsDisplayed()
        composeRule.onNodeWithText("Japan").assertIsDisplayed()
    }
}
