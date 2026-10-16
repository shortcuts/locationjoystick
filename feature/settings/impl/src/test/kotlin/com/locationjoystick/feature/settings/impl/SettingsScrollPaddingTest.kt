package com.locationjoystick.feature.settings.impl

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScrollPaddingTest {
    @Test
    fun bottomPaddingClearsSaveDiscardFabs() {
        val p = settingsScrollPadding()
        // 56dp FAB + 16dp scaffold margin must fit, with slack for large font scale.
        assertEquals(120.dp, p.calculateBottomPadding())
        assertEquals(16.dp, p.calculateTopPadding())
        assertEquals(16.dp, p.calculateLeftPadding(LayoutDirection.Ltr))
    }
}
