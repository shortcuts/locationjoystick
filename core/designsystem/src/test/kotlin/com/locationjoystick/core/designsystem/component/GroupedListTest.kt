package com.locationjoystick.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupedListTest {
    private data class Item(
        val id: String,
        val category: String?,
    )

    private fun group(items: List<Item>) = groupForDisplay(items, { it.category }, { it.id.startsWith("hot_") })

    @Test
    fun `user groups come first then ungrouped then hot countries`() {
        val result =
            group(
                listOf(
                    Item("hot_a", "United States"),
                    Item("u1", null),
                    Item("u2", "Local"),
                    Item("hot_b", "France"),
                ),
            )
        assertEquals(listOf("Local", null, "France", "United States"), result.map { it.name })
        assertEquals(listOf(true, false, false, false), result.map { it.isUserGroup })
    }

    @Test
    fun `user groups follow first appearance in the list`() {
        val result = group(listOf(Item("a", "Zoo"), Item("b", "Alpha"), Item("c", "Zoo")))

        assertEquals(listOf("Zoo", "Alpha"), result.map { it.name })
        assertEquals(listOf("a", "c"), result[0].items.map { it.id })
    }

    @Test
    fun `user group named like a hot country stays separate`() {
        val result = group(listOf(Item("u1", "France"), Item("hot_b", "France")))
        assertEquals(listOf(true, false), result.map { it.isUserGroup })
        assertEquals(listOf(listOf("u1"), listOf("hot_b")), result.map { g -> g.items.map { it.id } })
    }

    @Test
    fun `hot item without category lands in ungrouped`() {
        val result = group(listOf(Item("hot_route_x", null), Item("u1", null)))
        assertEquals(1, result.size)
        assertEquals(2, result[0].items.size)
    }

    private fun moved(
        items: List<Item>,
        key: String,
        delta: Int,
    ) = movedKeys(group(items), { it.id }, key, delta)

    @Test
    fun `row moves within its own section`() {
        val items = listOf(Item("a", "G"), Item("b", "G"), Item("c", null))
        assertEquals(listOf("b", "a", "c"), moved(items, "b", -1))
        assertEquals(null, moved(items, "b", 1))
        assertEquals(null, moved(items, "c", -1))
    }

    @Test
    fun `user group header moves among user groups`() {
        val items = listOf(Item("a", "X"), Item("b", "Y"), Item("c", null))
        assertEquals(listOf("b", "a", "c"), moved(items, "group_header_u:Y", -1))
        assertEquals(null, moved(items, "group_header_u:Y", 1))
    }
}
