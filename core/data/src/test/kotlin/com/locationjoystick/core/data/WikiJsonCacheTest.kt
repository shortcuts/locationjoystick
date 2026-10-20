package com.locationjoystick.core.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WikiJsonCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private val parse: (String) -> List<String>? = { body ->
        if (body.startsWith("ok:")) body.removePrefix("ok:").split(",") else null
    }

    @Test
    fun `constructor does no disk read`() {
        val cacheFile = File(tmp.root, "cache.json").apply { writeText("ok:Cached") }
        var seedReads = 0
        val cache =
            WikiJsonCache(cacheFile, {
                seedReads++
                "ok:Seed"
            }, parse, "http://unused")

        assertEquals(emptyList<String>(), cache.items.value)
        assertEquals(0, seedReads)
    }

    @Test
    fun `serves cached copy after load`() =
        runTest {
            val cacheFile = File(tmp.root, "cache.json").apply { writeText("ok:Cached") }
            val cache = WikiJsonCache(cacheFile, { "ok:Seed" }, parse, "http://unused")

            cache.load()

            assertEquals(listOf("Cached"), cache.items.value)
        }
}
