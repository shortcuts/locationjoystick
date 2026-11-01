package com.locationjoystick.core.designsystem

import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class LjTypographyTest {
    @Test
    fun everyStyleUsesInter() {
        val styles =
            with(LjTypography) {
                listOf(
                    displayLarge,
                    displayMedium,
                    displaySmall,
                    headlineLarge,
                    headlineMedium,
                    headlineSmall,
                    titleLarge,
                    titleMedium,
                    titleSmall,
                    bodyLarge,
                    bodyMedium,
                    bodySmall,
                    labelLarge,
                    labelMedium,
                    labelSmall,
                )
            }
        styles.forEach { assertEquals(InterFontFamily, it.fontFamily) }
    }

    @Test
    fun tabularEnablesTnum() {
        assertEquals("tnum", TextStyle.Default.tabular().fontFeatureSettings)
    }
}
