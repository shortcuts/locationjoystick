package com.locationjoystick.feature.joystick.impl

import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.MovementState
import com.locationjoystick.core.model.shouldIgnoreJoystickInput
import com.locationjoystick.core.model.shouldPreserveEngineMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JoystickInputGuardTest {
    @Test
    fun `ignores joystick while route replay is running`() {
        assertTrue(shouldIgnoreJoystickInput(MovementState.Active(MockMode.ROUTE_REPLAY, paused = false)))
    }

    @Test
    fun `allows joystick while route replay is paused`() {
        assertFalse(shouldIgnoreJoystickInput(MovementState.Active(MockMode.ROUTE_REPLAY, paused = true)))
    }

    @Test
    fun `allows joystick after route is stopped`() {
        assertFalse(shouldIgnoreJoystickInput(MovementState.Manual(MockMode.TELEPORT)))
        assertFalse(shouldIgnoreJoystickInput(MovementState.Manual(MockMode.JOYSTICK)))
    }

    @Test
    fun `ignores joystick while roaming is running`() {
        assertTrue(shouldIgnoreJoystickInput(MovementState.Active(MockMode.ROAMING, paused = false)))
    }

    @Test
    fun `allows joystick while roaming is paused`() {
        assertFalse(shouldIgnoreJoystickInput(MovementState.Active(MockMode.ROAMING, paused = true)))
    }

    @Test
    fun `ignores joystick during walk-to and follower`() {
        assertTrue(shouldIgnoreJoystickInput(MovementState.Active(MockMode.WALK_TO, paused = false)))
        assertTrue(shouldIgnoreJoystickInput(MovementState.Manual(MockMode.FOLLOWER)))
    }

    @Test
    fun `paused replay keeps engine mode so resume still owns the route`() {
        assertTrue(shouldPreserveEngineMode(MockMode.ROUTE_REPLAY))
        assertTrue(shouldPreserveEngineMode(MockMode.ROAMING))
        assertFalse(shouldPreserveEngineMode(MockMode.JOYSTICK))
        assertFalse(shouldPreserveEngineMode(MockMode.TELEPORT))
    }
}
