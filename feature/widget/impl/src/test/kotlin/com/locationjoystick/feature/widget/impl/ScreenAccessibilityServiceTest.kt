package com.locationjoystick.feature.widget.impl

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAccessibilityServiceTest {
    @Test
    fun belowApi30_unsupported() {
        assertFalse(ScreenAccessibilityService.isSupported(Build.VERSION_CODES.Q))
    }

    @Test
    fun api30AndAbove_supported() {
        assertTrue(ScreenAccessibilityService.isSupported(Build.VERSION_CODES.R))
        assertTrue(ScreenAccessibilityService.isSupported(Build.VERSION_CODES.TIRAMISU))
    }
}
