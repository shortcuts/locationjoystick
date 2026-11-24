package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.model.MockMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetParkControlsTest {
    @Test
    fun `route icon is active for route replay and walk-to only`() {
        assertEquals(true, routeControlsActive(MockMode.ROUTE_REPLAY))
        assertEquals(true, routeControlsActive(MockMode.WALK_TO))
        assertEquals(false, routeControlsActive(MockMode.ROAMING))
        assertEquals(false, routeControlsActive(MockMode.JOYSTICK))
        assertEquals(false, routeControlsActive(MockMode.TELEPORT))
    }

    @Test
    fun `joystick is moving only in joystick mode`() {
        assertEquals(true, joystickMoving(MockMode.JOYSTICK))
        assertEquals(false, joystickMoving(MockMode.TELEPORT))
        assertEquals(false, joystickMoving(MockMode.ROUTE_REPLAY))
    }
}
