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

    private fun treeOf(count: Int) =
        HotItemTree(
            allIds = (1..count).map { "id$it" }.toSet(),
            byCountry = mapOf("Japan" to (1..count).associate { "City$it" to listOf(HotItemEntry("id$it", "Spot$it")) }),
        )

    private fun setSection(tree: HotItemTree) {
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
    fun `tree at the threshold starts expanded`() {
        setSection(treeOf(8))
        composeRule.onNodeWithText("Japan").assertIsDisplayed()
    }

    @Test
    fun `tree above the threshold starts collapsed`() {
        setSection(treeOf(9))
        composeRule.onAllNodesWithText("Japan").assertCountEquals(0)
        composeRule.onNodeWithText("Hot locations").assertIsDisplayed()
    }

    @Test
    fun `header toggle expands a long tree`() {
        setSection(treeOf(9))
        composeRule.onNodeWithContentDescription("Expand").performClick()
        composeRule.onNodeWithContentDescription("Collapse").assertIsDisplayed()
        composeRule.onNodeWithText("Japan").assertIsDisplayed()
    }
}
