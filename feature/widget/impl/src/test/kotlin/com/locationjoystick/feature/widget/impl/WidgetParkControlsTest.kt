package com.locationjoystick.feature.widget.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetParkControlsTest {
    @Test
    fun `spoofing long-press shows Pause and Stop`() {
        assertEquals(WidgetMasterPopupMode.PAUSE_AND_STOP, widgetMasterPopupMode(spoofingActive = true))
    }

    @Test
    fun `parked long-press shows Start and Stop`() {
        assertEquals(WidgetMasterPopupMode.START_AND_STOP, widgetMasterPopupMode(spoofingActive = false))
    }

    @Test
    fun `feature controls work only while spoofing`() {
        assertEquals(true, widgetControlsEnabled(spoofingActive = true))
        assertEquals(false, widgetControlsEnabled(spoofingActive = false))
    }

    @Test
    fun `Stop stays available while parked`() {
        assertEquals(true, widgetStopEnabled(spoofingActive = false))
        assertEquals(true, widgetStopEnabled(spoofingActive = true))
    }
}
