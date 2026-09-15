package com.locationjoystick.feature.map.impl

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasteSheetDismissTest {
    @Test
    fun `user dismiss while the map is visible consumes the pending GPX`() {
        assertTrue(shouldHonorPasteSheetDismiss(Lifecycle.State.RESUMED))
        assertTrue(shouldHonorPasteSheetDismiss(Lifecycle.State.STARTED))
    }

    @Test
    fun `sheet teardown after ON_STOP does not consume the pending GPX`() {
        assertFalse(shouldHonorPasteSheetDismiss(Lifecycle.State.CREATED))
        assertFalse(shouldHonorPasteSheetDismiss(Lifecycle.State.INITIALIZED))
        assertFalse(shouldHonorPasteSheetDismiss(Lifecycle.State.DESTROYED))
    }
}
