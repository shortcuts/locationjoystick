package com.locationjoystick.feature.widget.impl

import android.view.Gravity
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasteOverlayWindowTest {
    @Test
    fun `paste overlay is a bottom wrap-content window`() {
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, pasteOverlayWidth())
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, pasteOverlayHeight())
        assertEquals(Gravity.BOTTOM or Gravity.START, pasteOverlayGravity())
    }

    @Test
    fun `expanded paste overlay stays touch-modal off so apps above the sheet can be used`() {
        val flags = pasteOverlayWindowFlags(minimized = false)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertEquals(0, flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    @Test
    fun `minimized paste overlay does not steal focus from the app underneath`() {
        val flags = pasteOverlayWindowFlags(minimized = true)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    }
}
