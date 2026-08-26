package com.locationjoystick.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesListFormatTest {
    @Test
    fun `formatLatLng rounds to 4 decimals and joins with comma`() {
        assertEquals("48.8566, 2.3522", formatLatLng(48.85655, 2.35222))
    }

    @Test
    fun `formatLatLng uses dot decimal separator regardless of default locale`() {
        val default = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("48.8566, 2.3522", formatLatLng(48.85655, 2.35222))
        } finally {
            java.util.Locale.setDefault(default)
        }
    }
}
