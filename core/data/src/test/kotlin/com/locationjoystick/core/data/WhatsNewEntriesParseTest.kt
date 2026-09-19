package com.locationjoystick.core.data

import com.locationjoystick.core.common.constants.AppConstants
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
    fun `wiki file for the current version has entries`() {
        val version = AppConstants.AppInfo.VERSION_NAME.substringBefore("-")
        val file =
            listOf(
                File("docs/wiki/changelog/$version.json"),
                File("../../docs/wiki/changelog/$version.json"),
            ).firstOrNull { it.isFile }
        assertTrue("missing docs/wiki/changelog/$version.json", file != null)
        val highlights = parseWhatsNewEntries(file!!.readText())
        assertTrue(!highlights.isNullOrEmpty())
    }
}
