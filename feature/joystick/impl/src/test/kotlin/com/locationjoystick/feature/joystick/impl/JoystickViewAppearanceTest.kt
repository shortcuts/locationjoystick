package com.locationjoystick.feature.joystick.impl

import com.locationjoystick.core.common.constants.AppConstants
import org.junit.Assert.assertTrue
import org.junit.Test

class JoystickViewAppearanceTest {
    @Test
    fun `outer disc opacity is a modest bump so it reads on light apps without going solid`() {
        val alpha = AppConstants.JoystickConstants.OUTER_ALPHA
        assertTrue(
            "OUTER_ALPHA was $alpha, expected 110..155 (old value was 80; 180+ is too solid)",
            alpha in 110..155,
        )
    }

    @Test
    fun `stick is more solid than the disc and has a soft edge for contrast`() {
        val joystick = AppConstants.JoystickConstants
        assertTrue(
            "KNOB_ALPHA was ${joystick.KNOB_ALPHA}, expected a near-solid light stick",
            joystick.KNOB_ALPHA >= 240,
        )
        assertTrue(
            "stick should stay more opaque than the disc",
            joystick.KNOB_ALPHA - joystick.OUTER_ALPHA >= 80,
        )
        assertTrue(
            "KNOB_EDGE_ALPHA was ${joystick.KNOB_EDGE_ALPHA}, expected a visible but not heavy ring",
            joystick.KNOB_EDGE_ALPHA in 100..180,
        )
    }

    @Test
    fun `outer ring is gray and slightly lighter than the stick edge`() {
        val joystick = AppConstants.JoystickConstants
        val outerLum = rgbLuminance(joystick.OUTER_BORDER_RGB)
        val stickLum = rgbLuminance(joystick.KNOB_EDGE_RGB)
        assertTrue("outer ring luminance $outerLum should be a mid gray, not white or black", outerLum in 80..160)
        assertTrue(
            "outer ring luminance $outerLum should be lighter than stick edge $stickLum",
            outerLum > stickLum + 20,
        )
    }

    private fun rgbLuminance(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return (r + g + b) / 3
    }
}
