package com.locationjoystick.core.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WikiSearchStyleTest {
    @Test
    fun `phone width keeps the search label visible`() {
        val css = readStylesheet()
        val phoneRule =
            Regex("""@media\(max-width:768px\)\{\s*\.DocSearch-Button-Placeholder\{display:inline-block !important\}""")
        assertTrue("style.css must show the DocSearch placeholder at phone width", phoneRule.containsMatchIn(css))
    }

    private fun readStylesheet(): String {
        val file = listOf(File("docs/wiki/style.css"), File("../../docs/wiki/style.css")).first { it.exists() }
        return file.readText()
    }
}
