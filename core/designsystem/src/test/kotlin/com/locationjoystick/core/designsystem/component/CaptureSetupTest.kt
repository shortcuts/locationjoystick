package com.locationjoystick.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSetupTest {
    @Test
    fun `toggle marker is needed when capture is off`() {
        assertEquals(CaptureStepMarker.NEEDED, captureToggleMarker(enabled = false))
    }

    @Test
    fun `toggle marker is done when capture is on`() {
        assertEquals(CaptureStepMarker.DONE, captureToggleMarker(enabled = true))
    }

    @Test
    fun `browser marker is needed until this app is default`() {
        assertEquals(CaptureStepMarker.NEEDED, captureBrowserMarker(isDefaultBrowser = false))
        assertEquals(CaptureStepMarker.DONE, captureBrowserMarker(isDefaultBrowser = true))
    }

    @Test
    fun `restore step is highlighted with number style while capture is off`() {
        assertEquals(CaptureStepMarker.RESTORE, captureRestoreMarker(functionEnabled = false))
        assertEquals(CaptureStepMarker.IDLE, captureRestoreMarker(functionEnabled = true))
    }

    @Test
    fun `ready when capture or jump is on and this app is the default browser`() {
        assertFalse(isCaptureReady(false, captureEnabled = false, jumpEnabled = false, isDefaultBrowser = false))
        assertFalse(isCaptureReady(true, captureEnabled = true, jumpEnabled = false, isDefaultBrowser = false))
        assertFalse(isCaptureReady(true, captureEnabled = false, jumpEnabled = false, isDefaultBrowser = true))
        assertFalse(isCaptureReady(false, captureEnabled = true, jumpEnabled = true, isDefaultBrowser = true))
        assertTrue(isCaptureReady(true, captureEnabled = true, jumpEnabled = false, isDefaultBrowser = true))
        assertTrue(isCaptureReady(true, captureEnabled = false, jumpEnabled = true, isDefaultBrowser = true))
    }
}
