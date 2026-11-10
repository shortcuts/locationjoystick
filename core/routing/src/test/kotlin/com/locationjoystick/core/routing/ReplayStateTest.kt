package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayStateTest {
    private val a = LatLng(0.0, 0.0)
    private val b = LatLng(1.0, 0.0)
    private val snapshot = ReplaySnapshot(position = a, waypointIndex = 1, waypoints = listOf(a, b), speedMs = 1.4)
    private val playing = ReplayState.Playing(snapshot)
    private val paused = ReplayState.Paused(snapshot)

    @Test
    fun `playing pauses and keeps the snapshot`() {
        assertEquals(paused, playing.paused())
    }

    @Test
    fun `pause from paused or idle is rejected`() {
        assertNull(paused.paused())
        assertNull(ReplayState.Idle.paused())
    }

    @Test
    fun `paused resumes from the steered position`() {
        assertEquals(ReplayState.Playing(snapshot.copy(position = b)), paused.resumed(b))
    }

    @Test
    fun `paused resumes from the saved position when none is given`() {
        assertEquals(playing, paused.resumed(null))
    }

    @Test
    fun `resume from idle is rejected`() {
        assertNull(ReplayState.Idle.resumed(null))
    }

    @Test
    fun `progress is recorded only while playing`() {
        assertEquals(ReplayState.Playing(snapshot.copy(position = b, waypointIndex = 2)), playing.progressed(b, 2))
        assertNull(paused.progressed(b, 2))
        assertNull(ReplayState.Idle.progressed(b, 2))
    }

    @Test
    fun `edit applies in playing and paused but not idle`() {
        assertEquals(ReplayState.Playing(snapshot.copy(speedMs = 5.0)), playing.edited { it.copy(speedMs = 5.0) })
        assertEquals(ReplayState.Paused(snapshot.copy(speedMs = 5.0)), paused.edited { it.copy(speedMs = 5.0) })
        assertNull(ReplayState.Idle.edited { it.copy(speedMs = 5.0) })
    }

    @Test
    fun `only playing is playing`() {
        assertTrue(playing.isPlaying)
        assertFalse(paused.isPlaying)
        assertFalse(ReplayState.Idle.isPlaying)
    }
}
