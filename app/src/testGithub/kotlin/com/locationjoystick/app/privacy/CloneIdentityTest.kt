package com.locationjoystick.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CloneIdentityTest {
    private val running = "com.locationjoystick.app"

    @Test
    fun `package id has two or three valid segments and avoids banned words`() {
        val segment = Regex("[a-z][a-z0-9]{3,7}")
        repeat(2000) { seed ->
            val parts = generatePackageId(Random(seed), running).split(".")
            assertTrue(parts.size in 2..3)
            assertTrue(parts.first() in setOf("com", "net", "org", "io", "app"))
            parts.drop(1).forEach { assertTrue(it, segment.matches(it)) }
            BANNED_WORDS.forEach { assertFalse(parts.joinToString("."), it in parts.joinToString(".")) }
            assertFalse(parts.joinToString(".") == running)
        }
    }

    @Test
    fun `same seed gives same package id`() {
        assertEquals(generatePackageId(Random(7), running), generatePackageId(Random(7), running))
    }

    @Test
    fun `name rule accepts 1 to 30 trimmed characters`() {
        assertFalse(isValidCloneName(""))
        assertFalse(isValidCloneName("   "))
        assertTrue(isValidCloneName("a"))
        assertTrue(isValidCloneName("a".repeat(30)))
        assertFalse(isValidCloneName("a".repeat(31)))
        assertTrue(isValidCloneName("  " + "a".repeat(30) + "  "))
    }

    @Test
    fun `rename covers exact and dotted values only`() {
        val old = "com.locationjoystick.app"
        assertEquals("com.xq7.notes", renameWithPackage(old, old, "com.xq7.notes"))
        assertEquals("com.xq7.notes.fileprovider", renameWithPackage("$old.fileprovider", old, "com.xq7.notes"))
        assertNull(renameWithPackage("androidx.core.content.FileProvider", old, "com.xq7.notes"))
        assertNull(renameWithPackage("${old}x.fileprovider", old, "com.xq7.notes"))
    }

    @Test
    fun `authorities rename each part`() {
        val old = "com.locationjoystick.app"
        assertEquals("n.p.a;n.p.b", renameAuthorities("$old.a;$old.b", old, "n.p"))
        assertEquals("other.a;n.p.b", renameAuthorities("other.a;$old.b", old, "n.p"))
    }

    @Test
    fun `background colour is opaque and follows the hue`() {
        fun channels(hue: Int): Triple<Int, Int, Int> {
            val c = backgroundArgb(hue)
            assertEquals(0xFF, c ushr 24)
            return Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        }
        val (r0, g0, b0) = channels(0)
        assertTrue(r0 > g0 && r0 > b0)
        val (r1, g1, b1) = channels(120)
        assertTrue(g1 > r1 && g1 > b1)
        val (r2, g2, b2) = channels(240)
        assertTrue(b2 > r2 && b2 > g2)
    }
}
