package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementStateTest {
    @Test
    fun `enterMode builds Active for engine modes and Manual otherwise`() {
        assertEquals(MovementState.Running(ActiveMode.WALK_TO), enterMode(MockMode.WALK_TO))
        assertEquals(MovementState.Running(ActiveMode.ROAMING), enterMode(MockMode.ROAMING))
        assertEquals(MovementState.Running(ActiveMode.ROUTE_REPLAY), enterMode(MockMode.ROUTE_REPLAY))
        assertEquals(MovementState.Manual(MockMode.TELEPORT), enterMode(MockMode.TELEPORT))
        assertEquals(MovementState.Manual(MockMode.JOYSTICK), enterMode(MockMode.JOYSTICK))
        assertEquals(MovementState.Manual(MockMode.FOLLOWER), enterMode(MockMode.FOLLOWER))
    }

    @Test
    fun `pause and resume round-trip for walk and roam`() {
        listOf(MockMode.WALK_TO, MockMode.ROAMING).forEach {
            val paused = enterMode(it).pausedOrNull()
            assertEquals(MovementState.Paused(ActiveMode.valueOf(it.name)), paused)
            assertEquals(MovementState.Running(ActiveMode.valueOf(it.name)), paused!!.resumedOrNull())
        }
    }

    @Test
    fun `pause is rejected from manual, replay and already paused`() {
        assertNull(enterMode(MockMode.TELEPORT).pausedOrNull())
        assertNull(enterMode(MockMode.JOYSTICK).pausedOrNull())
        assertNull(enterMode(MockMode.ROUTE_REPLAY).pausedOrNull())
        assertNull(MovementState.Paused(ActiveMode.ROAMING).pausedOrNull())
    }

    @Test
    fun `resume is rejected when not paused`() {
        assertNull(enterMode(MockMode.WALK_TO).resumedOrNull())
        assertNull(enterMode(MockMode.TELEPORT).resumedOrNull())
        assertNull(MovementState.Paused(ActiveMode.ROUTE_REPLAY).resumedOrNull())
    }

    @Test
    fun `derived properties follow the state`() {
        val paused = MovementState.Paused(ActiveMode.ROAMING)
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
