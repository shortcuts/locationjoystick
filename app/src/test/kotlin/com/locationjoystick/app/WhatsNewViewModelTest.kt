package com.locationjoystick.app

import com.locationjoystick.core.data.WhatsNewEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsNewViewModelTest {
    @Test
    fun `feat entries group before fix`() {
        val entries =
            listOf(
                WhatsNewEntry("fix", "General", "a fix"),
                WhatsNewEntry("feat", "General", "a feature"),
            )
        val groups = groupWhatsNewEntries(entries)
        assertEquals(listOf("New & Improved", "Fixes"), groups.map { it.label })
    }

    @Test
    fun `category with zero entries is omitted entirely`() {
        val entries = listOf(WhatsNewEntry("feat", "General", "a feature"))
        val groups = groupWhatsNewEntries(entries)
        assertEquals(listOf("New & Improved"), groups.map { it.label })
    }

    @Test
    fun `scopes within a category come out alphabetically regardless of input order`() {
        val entries =
            listOf(
                WhatsNewEntry("feat", "Roaming Mode", "z"),
                WhatsNewEntry("feat", "Floating Widget", "a"),
                WhatsNewEntry("feat", "General", "m"),
            )
        val groups = groupWhatsNewEntries(entries)
        assertEquals(
            listOf("Floating Widget", "General", "Roaming Mode"),
            groups.single().scopeGroups.map { it.scope },
        )
    }

    @Test
    fun `multiple entries for the same scope stay together in original order`() {
        val entries =
            listOf(
                WhatsNewEntry("feat", "General", "first"),
                WhatsNewEntry("feat", "General", "second"),
            )
        val groups = groupWhatsNewEntries(entries)
        assertEquals(
            listOf("first", "second"),
            groups
                .single()
                .scopeGroups
                .single()
                .entries
                .map { it.summary },
        )
    }
}
