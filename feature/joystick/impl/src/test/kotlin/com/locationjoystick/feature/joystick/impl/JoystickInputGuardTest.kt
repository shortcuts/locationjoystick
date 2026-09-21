package com.locationjoystick.feature.joystick.impl

import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.shouldIgnoreJoystickInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JoystickInputGuardTest {
    @Test
    fun `ignores joystick while route replay is running`() {
        assertTrue(shouldIgnoreJoystickInput(MockMode.ROUTE_REPLAY, MockLocationState.RUNNING))
    }

    @Test
    fun `allows joystick while route replay is paused`() {
        assertFalse(shouldIgnoreJoystickInput(MockMode.ROUTE_REPLAY, MockLocationState.PAUSED))
    }

    @Test
    fun `allows joystick after route is stopped`() {
        assertFalse(shouldIgnoreJoystickInput(MockMode.TELEPORT, MockLocationState.RUNNING))
        assertFalse(shouldIgnoreJoystickInput(MockMode.JOYSTICK, MockLocationState.RUNNING))
    }

    @Test
    fun `ignores joystick while roaming is running`() {
        assertTrue(shouldIgnoreJoystickInput(MockMode.ROAMING, MockLocationState.RUNNING))
    }

    @Test
    fun `allows joystick while roaming is paused`() {
        assertFalse(
            shouldIgnoreJoystickInput(
                MockMode.ROAMING,
                MockLocationState.RUNNING,
                isRoamingPaused = true,
            ),
        )
    }

    @Test
    fun `ignores joystick during walk-to and follower`() {
        assertTrue(shouldIgnoreJoystickInput(MockMode.WALK_TO, MockLocationState.RUNNING))
        assertTrue(shouldIgnoreJoystickInput(MockMode.FOLLOWER, MockLocationState.RUNNING))
    }

    @Test
    fun `paused replay keeps engine mode so resume still owns the route`() {
        assertTrue(shouldPreserveEngineMode(MockMode.ROUTE_REPLAY))
        assertTrue(shouldPreserveEngineMode(MockMode.ROAMING))
        assertFalse(shouldPreserveEngineMode(MockMode.JOYSTICK))
        assertFalse(shouldPreserveEngineMode(MockMode.TELEPORT))
    }
}
