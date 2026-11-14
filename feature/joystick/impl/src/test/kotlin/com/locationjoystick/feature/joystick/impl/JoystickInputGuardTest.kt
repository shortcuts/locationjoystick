package com.locationjoystick.feature.joystick.impl

import com.locationjoystick.core.model.ActiveMode
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
        assertTrue(shouldIgnoreJoystickInput(MovementState.Running(ActiveMode.ROUTE_REPLAY)))
    }

    @Test
    fun `allows joystick while route replay is paused`() {
        assertFalse(shouldIgnoreJoystickInput(MovementState.Paused(ActiveMode.ROUTE_REPLAY)))
    }

    @Test
    fun `allows joystick after route is stopped`() {
        assertFalse(shouldIgnoreJoystickInput(MovementState.Manual(MockMode.TELEPORT)))
        assertFalse(shouldIgnoreJoystickInput(MovementState.Manual(MockMode.JOYSTICK)))
    }

    @Test
    fun `ignores joystick while roaming is running`() {
        assertTrue(shouldIgnoreJoystickInput(MovementState.Running(ActiveMode.ROAMING)))
    }

    @Test
    fun `allows joystick while roaming is paused`() {
        assertFalse(shouldIgnoreJoystickInput(MovementState.Paused(ActiveMode.ROAMING)))
    }

    @Test
    fun `ignores joystick during walk-to and follower`() {
        assertTrue(shouldIgnoreJoystickInput(MovementState.Running(ActiveMode.WALK_TO)))
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
