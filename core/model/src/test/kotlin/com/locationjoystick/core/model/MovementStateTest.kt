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
    fun `pause and resume round-trip for every active mode`() {
        ActiveMode.entries.forEach {
            val paused = MovementState.Running(it).pausedOrNull()
            assertEquals(MovementState.Paused(it), paused)
            assertEquals(MovementState.Running(it), paused!!.resumedOrNull())
        }
    }

    @Test
    fun `pause is rejected from manual, pending and already paused`() {
        assertNull(enterMode(MockMode.TELEPORT).pausedOrNull())
        assertNull(enterMode(MockMode.JOYSTICK).pausedOrNull())
        assertNull(MovementState.PausePending(MockMode.TELEPORT, ActiveMode.WALK_TO).pausedOrNull())
        assertNull(MovementState.Paused(ActiveMode.ROAMING).pausedOrNull())
    }

    @Test
    fun `resume is rejected when not paused`() {
        assertNull(enterMode(MockMode.WALK_TO).resumedOrNull())
        assertNull(enterMode(MockMode.TELEPORT).resumedOrNull())
    }

    @Test
    fun `resume of a pending pause returns to the manual mode`() {
        assertEquals(
            MovementState.Manual(MockMode.JOYSTICK),
            MovementState.PausePending(MockMode.JOYSTICK, ActiveMode.ROUTE_REPLAY).resumedOrNull(),
        )
    }

    @Test
    fun `entering the pending mode turns a pending pause into Paused`() {
        val pending = MovementState.PausePending(MockMode.TELEPORT, ActiveMode.WALK_TO)
        assertEquals(MovementState.Paused(ActiveMode.WALK_TO), pending.afterEntering(MockMode.WALK_TO))
    }

    @Test
    fun `entering another mode drops a pending pause`() {
        val pending = MovementState.PausePending(MockMode.TELEPORT, ActiveMode.WALK_TO)
        assertEquals(MovementState.Running(ActiveMode.ROAMING), pending.afterEntering(MockMode.ROAMING))
        assertEquals(MovementState.Manual(MockMode.JOYSTICK), pending.afterEntering(MockMode.JOYSTICK))
    }

    @Test
    fun `entering a mode from any other state resets pause`() {
        assertEquals(
            MovementState.Running(ActiveMode.ROUTE_REPLAY),
            MovementState.Paused(ActiveMode.ROUTE_REPLAY).afterEntering(MockMode.ROUTE_REPLAY),
        )
    }

    @Test
    fun `a pending pause counts as paused for its mode only`() {
        val pending = MovementState.PausePending(MockMode.TELEPORT, ActiveMode.ROUTE_REPLAY)
        assertTrue(pending.isPaused)
        assertTrue(pending.isPaused(MockMode.ROUTE_REPLAY))
        assertFalse(pending.isPaused(MockMode.WALK_TO))
        assertFalse(pending.isActive)
        assertEquals(MockMode.TELEPORT, pending.mode)
        assertTrue(pending.pausesReplay)
        assertTrue(MovementState.Paused(ActiveMode.ROUTE_REPLAY).pausesReplay)
        assertFalse(MovementState.Running(ActiveMode.ROUTE_REPLAY).pausesReplay)
        assertFalse(MovementState.Paused(ActiveMode.WALK_TO).pausesReplay)
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
