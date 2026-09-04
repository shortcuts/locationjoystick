package com.locationjoystick.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `isValidLatLng accepts in-range coordinates`() {
        assertTrue(isValidLatLng(35.6762, 139.6503))
    }

    @Test
    fun `isValidLatLng rejects null values`() {
        assertFalse(isValidLatLng(null, 139.6503))
        assertFalse(isValidLatLng(35.6762, null))
    }

    @Test
    fun `isValidLatLng rejects out-of-range values`() {
        assertFalse(isValidLatLng(91.0, 0.0))
        assertFalse(isValidLatLng(0.0, 181.0))
        assertFalse(isValidLatLng(-91.0, 0.0))
        assertFalse(isValidLatLng(0.0, -181.0))
    }
}
