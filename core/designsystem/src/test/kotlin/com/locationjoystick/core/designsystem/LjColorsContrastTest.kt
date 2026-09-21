package com.locationjoystick.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LjColorsContrastTest {
    @Test
    fun darkOutlineVariantIsLighterThanDarkSurfaces() {
        assertTrue(channelSum(LjDarkOutlineVariant) > channelSum(LjSurface))
        assertTrue(channelSum(LjDarkOutlineVariant) > channelSum(LjBg))
    }

    @Test
    fun cardsAndSheetsAreSeparatedFromBackground() {
        listOf(LjDarkColorScheme, LjLightColorScheme).forEach { scheme ->
            assertNotEquals(scheme.background, scheme.surfaceVariant)
            assertNotEquals(scheme.background, scheme.surfaceContainerLow)
            assertNotEquals(scheme.surfaceVariant, scheme.surfaceContainerLow)
        }
    }

    private fun channelSum(color: Color): Float = color.red + color.green + color.blue
}
