package com.locationjoystick.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberInputUtilsTest {
    @Test
    fun `comma decimal separator parses to exact fraction, not rounded`() {
        assertEquals(0.8, "0,8".toLocaleDoubleOrNull()!!, 0.0)
    }

    @Test
    fun `dot decimal separator still parses`() {
        assertEquals(0.8, "0.8".toLocaleDoubleOrNull()!!, 0.0)
    }

    @Test
    fun `invalid input returns null`() {
        assertNull("abc".toLocaleDoubleOrNull())
    }
}
