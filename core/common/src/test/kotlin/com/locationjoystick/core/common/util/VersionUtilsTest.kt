package com.locationjoystick.core.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUtilsTest {
    @Test
    fun `newer major minor patch are newer`() {
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
        assertTrue(isNewerVersion("1.3.0", "1.2.9"))
        assertTrue(isNewerVersion("1.2.4", "1.2.3"))
        assertTrue(isNewerVersion("0.22.0", "0.9.0"))
    }

    @Test
    fun `equal and older are not newer`() {
        assertFalse(isNewerVersion("1.2.3", "1.2.3"))
        assertFalse(isNewerVersion("1.2.2", "1.2.3"))
    }

    @Test
    fun `v prefix and pre-release suffix are stripped`() {
        assertTrue(isNewerVersion("v1.2.4", "1.2.3"))
        assertFalse(isNewerVersion("v1.2.3", "v1.2.3"))
        assertFalse(isNewerVersion("1.2.3-alpha1", "1.2.3"))
    }

    @Test
    fun `missing segments count as zero`() {
        assertFalse(isNewerVersion("1.2", "1.2.0"))
        assertTrue(isNewerVersion("1.2.1", "1.2"))
    }

    @Test
    fun `malformed input is never newer`() {
        assertFalse(isNewerVersion("abc", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "abc"))
    }
}
