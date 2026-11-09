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
    fun `joystick is ignored while an active mode runs`() {
        listOf(MockMode.ROUTE_REPLAY, MockMode.ROAMING, MockMode.WALK_TO).forEach {
            assertTrue(shouldIgnoreJoystickInput(MovementState.Active(it, paused = false)))
        }
    }

    @Test
    fun `joystick works while an active mode is paused`() {
        listOf(MockMode.ROUTE_REPLAY, MockMode.ROAMING, MockMode.WALK_TO).forEach {
            assertFalse(shouldIgnoreJoystickInput(MovementState.Active(it, paused = true)))
        }
    }

    @Test
    fun `joystick works when neither route nor roam owns the session`() {
        assertFalse(shouldIgnoreJoystickInput(MovementState.Manual(MockMode.TELEPORT)))
        assertFalse(shouldIgnoreJoystickInput(MovementState.Manual(MockMode.JOYSTICK)))
    }

    @Test
    fun `follower owns the tick over joystick`() {
        assertTrue(shouldIgnoreJoystickInput(MovementState.Manual(MockMode.FOLLOWER)))
    }
}
