package com.locationjoystick.feature.widget.impl

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScreenAccessibilityServiceTest {
    @Test
    fun belowApi30_unsupported() {
        assertFalse(ScreenAccessibilityService.isSupported(Build.VERSION_CODES.Q))
    }

    @Test
    fun lumaInto_weightsEachChannelRec601() {
        val red = 0xFFFF0000.toInt()
        val green = 0xFF00FF00.toInt()
        val blue = 0xFF0000FF.toInt()
        assertEquals(listOf(76, 149, 29), lumaValues(intArrayOf(red, green, blue)))
    }

    @Test
    fun lumaInto_ignoresAlpha() {
        assertEquals(listOf(76), lumaValues(intArrayOf(0x00FF0000)))
    }

    private fun lumaValues(pixels: IntArray): List<Int> {
        val out = ByteArray(pixels.size)
        lumaInto(pixels, out)
        return out.map { it.toInt() and 0xFF }
    }
}
