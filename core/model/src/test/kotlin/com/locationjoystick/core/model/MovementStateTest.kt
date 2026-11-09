package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementStateTest {
    @Test
    fun `enterMode builds Active for engine modes and Manual otherwise`() {
        assertEquals(MovementState.Active(MockMode.WALK_TO, false), enterMode(MockMode.WALK_TO))
        assertEquals(MovementState.Active(MockMode.ROAMING, false), enterMode(MockMode.ROAMING))
        assertEquals(MovementState.Active(MockMode.ROUTE_REPLAY, false), enterMode(MockMode.ROUTE_REPLAY))
        assertEquals(MovementState.Manual(MockMode.TELEPORT), enterMode(MockMode.TELEPORT))
        assertEquals(MovementState.Manual(MockMode.JOYSTICK), enterMode(MockMode.JOYSTICK))
        assertEquals(MovementState.Manual(MockMode.FOLLOWER), enterMode(MockMode.FOLLOWER))
    }

    @Test
    fun `pause and resume round-trip for walk and roam`() {
        listOf(MockMode.WALK_TO, MockMode.ROAMING).forEach {
            val paused = enterMode(it).pausedOrNull()
            assertEquals(MovementState.Active(it, true), paused)
            assertEquals(MovementState.Active(it, false), paused!!.resumedOrNull())
        }
    }

    @Test
    fun `pause is rejected from manual, replay and already paused`() {
        assertNull(enterMode(MockMode.TELEPORT).pausedOrNull())
        assertNull(enterMode(MockMode.JOYSTICK).pausedOrNull())
        assertNull(enterMode(MockMode.ROUTE_REPLAY).pausedOrNull())
        assertNull(MovementState.Active(MockMode.ROAMING, true).pausedOrNull())
    }

    @Test
    fun `resume is rejected when not paused`() {
        assertNull(enterMode(MockMode.WALK_TO).resumedOrNull())
        assertNull(enterMode(MockMode.TELEPORT).resumedOrNull())
        assertNull(MovementState.Active(MockMode.ROUTE_REPLAY, true).resumedOrNull())
    }

    @Test
    fun `derived properties follow the state`() {
        val paused = MovementState.Active(MockMode.ROAMING, true)
        assertTrue(paused.isActive)
        assertTrue(paused.isPaused)
        assertTrue(paused.isActive(MockMode.ROAMING))
        assertTrue(paused.isPaused(MockMode.ROAMING))
        assertFalse(paused.isActive(MockMode.WALK_TO))
        assertFalse(paused.isPaused(MockMode.WALK_TO))
        val manual = MovementState.Manual(MockMode.TELEPORT)
        assertFalse(manual.isActive)
        assertFalse(manual.isPaused)
    }
}
