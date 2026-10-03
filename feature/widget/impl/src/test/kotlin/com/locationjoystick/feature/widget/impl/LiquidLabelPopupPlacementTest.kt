package com.locationjoystick.feature.widget.impl

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidLabelPopupPlacementTest {
    @Test
    fun `right label source remains centered on the original icon`() {
        val anchor = IntRect(0, 100, 50, 152)
        val placement = liquidLabelPopupPlacement(0, anchor, IntSize(146, 52), 21, 360)
        assertFalse(placement.expandLeft)
        assertEquals(anchor.center.x, placement.offset.x + 21)
        assertEquals(anchor.center.y, placement.offset.y + 26)
    }

    @Test
    fun `left label fits right edge and source still aligns after dragging`() {
        val anchor = IntRect(0, 100, 50, 152)
        for (parentX in 220..310) {
            val placement = liquidLabelPopupPlacement(parentX, anchor, IntSize(146, 52), 21, 360)
            assertTrue(placement.expandLeft)
            assertTrue(parentX + placement.offset.x >= 0)
            assertEquals(anchor.center.x, placement.offset.x + 146 - 21)
        }
    }

    @Test
    fun `larger font height stays centered on source instead of shifting the neck`() {
        val anchor = IntRect(0, 100, 50, 152)
        val placement = liquidLabelPopupPlacement(0, anchor, IntSize(180, 80), 21, 360)
        assertEquals(anchor.center.y, placement.offset.y + 40)
    }
}
