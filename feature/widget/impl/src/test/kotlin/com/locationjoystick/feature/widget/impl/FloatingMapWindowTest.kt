package com.locationjoystick.feature.widget.impl

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMapWindowTest {
    @Test
    fun `compact map uses part of a phone screen`() {
        assertEquals(886, compactFloatingMapWidth(screenWidthPx = 1080, density = 3f))
        assertEquals(1392, compactFloatingMapHeight(screenHeightPx = 2400, density = 3f))
    }

    @Test
    fun `touches outside map window pass through`() {
        assertTrue(floatingMapWindowFlags() and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
    }
}
