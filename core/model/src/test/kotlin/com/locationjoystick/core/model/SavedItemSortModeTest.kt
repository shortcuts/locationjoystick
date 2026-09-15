package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedItemSortModeTest {
    private data class Item(
        val name: String,
        override val createdAt: Long,
    ) : HasCreatedAt

    private val items = listOf(Item("beta", 200L), Item("Alpha", 300L), Item("charlie", 100L))

    @Test
    fun `supports name and saved-time orderings`() {
        assertEquals(
            listOf("Alpha", "beta", "charlie"),
            items.sortedBySavedItemMode(SavedItemSortMode.NAME_ASCENDING) { it.name }.map { it.name },
        )
        assertEquals(
            listOf("charlie", "beta", "Alpha"),
            items.sortedBySavedItemMode(SavedItemSortMode.NAME_DESCENDING) { it.name }.map { it.name },
        )
        assertEquals(
            listOf("Alpha", "beta", "charlie"),
            items.sortedBySavedItemMode(SavedItemSortMode.NEWEST_FIRST) { it.name }.map { it.name },
        )
        assertEquals(
            listOf("charlie", "beta", "Alpha"),
            items.sortedBySavedItemMode(SavedItemSortMode.OLDEST_FIRST) { it.name }.map { it.name },
        )
    }
}
