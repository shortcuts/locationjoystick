package com.locationjoystick.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WhatsNewEntriesParseTest {
    @Test
    fun `parses an entries array`() {
        val body =
            """{"entries":[
                {"category":"feat","scope":"General","summary":"First"},
                {"category":"fix","scope":"General","summary":"Second"}
            ]}"""
        assertEquals(
            listOf(WhatsNewEntry("feat", "General", "First"), WhatsNewEntry("fix", "General", "Second")),
            parseWhatsNewEntries(body),
        )
    }

    @Test
    fun `empty entries is missing`() {
        assertNull(parseWhatsNewEntries("""{"version":"0.20.16","entries":[]}"""))
    }

    @Test
    fun `invalid json is missing`() {
        assertNull(parseWhatsNewEntries("not-json"))
    }

    @Test
    fun `newest wiki changelog file has entries`() {
        val dir =
            listOf(File("docs/wiki/changelog"), File("../../docs/wiki/changelog")).firstOrNull { it.isDirectory }
        assertTrue("missing docs/wiki/changelog", dir != null)
        // Newest, not VERSION_NAME: a tagged version's JSON is frozen, so the next release's file lands before the bump.
        val newest =
            dir!!
                .listFiles { f -> f.extension == "json" }!!
                .maxWith(compareBy({ v(it, 0) }, { v(it, 1) }, { v(it, 2) }))
        val highlights = parseWhatsNewEntries(newest.readText())
        assertTrue("no entries in ${newest.name}", !highlights.isNullOrEmpty())
    }

    private fun v(
        file: File,
        index: Int,
    ) = file.nameWithoutExtension.split(".")[index].toInt()
}
