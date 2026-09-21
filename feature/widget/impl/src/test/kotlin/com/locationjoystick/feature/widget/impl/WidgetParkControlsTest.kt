package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.model.MockMode
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
        assertEquals(true, widgetStopEnabled())
    }

    @Test
    fun `route icon is active for route replay and walk-to only`() {
        assertEquals(true, routeControlsActive(MockMode.ROUTE_REPLAY))
        assertEquals(true, routeControlsActive(MockMode.WALK_TO))
        assertEquals(false, routeControlsActive(MockMode.ROAMING))
        assertEquals(false, routeControlsActive(MockMode.JOYSTICK))
        assertEquals(false, routeControlsActive(MockMode.TELEPORT))
    }
}
