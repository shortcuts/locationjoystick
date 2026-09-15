package com.locationjoystick.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementPriorityTest {
    @Test
    fun `route playing blocks roaming start`() {
        assertFalse(canStartRoaming(MockMode.ROUTE_REPLAY, MockLocationState.RUNNING))
    }

    @Test
    fun `paused or stopped route allows roaming start`() {
        assertTrue(canStartRoaming(MockMode.ROUTE_REPLAY, MockLocationState.PAUSED))
        assertTrue(canStartRoaming(MockMode.TELEPORT, MockLocationState.RUNNING))
        assertTrue(canStartRoaming(MockMode.JOYSTICK, MockLocationState.RUNNING))
    }

    @Test
    fun `joystick is ignored while a route is playing`() {
        assertTrue(shouldIgnoreJoystickInput(MockMode.ROUTE_REPLAY, MockLocationState.RUNNING))
    }

    @Test
    fun `joystick works while a route is paused`() {
        assertFalse(shouldIgnoreJoystickInput(MockMode.ROUTE_REPLAY, MockLocationState.PAUSED))
    }

    @Test
    fun `joystick is ignored while roaming is running`() {
        assertTrue(shouldIgnoreJoystickInput(MockMode.ROAMING, MockLocationState.RUNNING))
        assertTrue(shouldIgnoreJoystickInput(MockMode.ROAMING, MockLocationState.PAUSED))
    }

    @Test
    fun `joystick works while roaming is paused`() {
        assertFalse(
            shouldIgnoreJoystickInput(
                MockMode.ROAMING,
                MockLocationState.RUNNING,
                isRoamingPaused = true,
            ),
        )
    }

    @Test
    fun `joystick works when neither route nor roam owns the session`() {
        assertFalse(shouldIgnoreJoystickInput(MockMode.TELEPORT, MockLocationState.RUNNING))
        assertFalse(shouldIgnoreJoystickInput(MockMode.JOYSTICK, MockLocationState.RUNNING))
    }

    @Test
    fun `walk-to and follower still own the tick over joystick`() {
        assertTrue(shouldIgnoreJoystickInput(MockMode.WALK_TO, MockLocationState.RUNNING))
        assertTrue(shouldIgnoreJoystickInput(MockMode.FOLLOWER, MockLocationState.RUNNING))
        assertTrue(shouldIgnoreJoystickInput(MockMode.WALK_TO, MockLocationState.PAUSED))
    }
}
