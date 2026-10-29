package com.locationjoystick.feature.settings.impl

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScrollPaddingTest {
    @Test
    fun paddingIsUniform() {
        val p = settingsScrollPadding()
        assertEquals(16.dp, p.calculateBottomPadding())
        assertEquals(16.dp, p.calculateTopPadding())
        assertEquals(16.dp, p.calculateLeftPadding(LayoutDirection.Ltr))
    }
}
