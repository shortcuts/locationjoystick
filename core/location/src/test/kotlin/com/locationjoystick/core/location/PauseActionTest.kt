package com.locationjoystick.core.location

import com.locationjoystick.core.data.RoadRouteFetch
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.MovementState
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Test

class PauseActionTest {
    private val idle = RoadRouteFetch.Idle
    private val walkFetch = RoadRouteFetch.WalkFetch(Job())
    private val running = MockLocationState.RUNNING

    private fun active(
        mode: MockMode,
        paused: Boolean = false,
    ) = MovementState.Active(mode, paused)

    @Test
    fun `follower leaves follow leader`() =
        assertEquals(PauseAction.LeaveFollower, pauseActionFor(MovementState.Manual(MockMode.FOLLOWER), idle, running))

    @Test
    fun `joystick with nothing running does nothing`() =
        assertEquals(PauseAction.None, pauseActionFor(MovementState.Manual(MockMode.JOYSTICK), idle, running))

    @Test
    fun `walk pauses`() = assertEquals(PauseAction.PauseWalk, pauseActionFor(active(MockMode.WALK_TO), idle, running))

    @Test
    fun `paused walk does nothing`() = assertEquals(PauseAction.None, pauseActionFor(active(MockMode.WALK_TO, true), idle, running))

    @Test
    fun `walk fetch pauses the walk before the mode is WALK_TO`() =
        assertEquals(PauseAction.PauseWalk, pauseActionFor(MovementState.Manual(MockMode.TELEPORT), walkFetch, running))

    @Test
    fun `walk fetch outranks roaming`() = assertEquals(PauseAction.PauseWalk, pauseActionFor(active(MockMode.ROAMING), walkFetch, running))

    @Test
    fun `roaming pauses`() = assertEquals(PauseAction.PauseRoaming, pauseActionFor(active(MockMode.ROAMING), idle, running))

    @Test
    fun `paused roaming does nothing`() = assertEquals(PauseAction.None, pauseActionFor(active(MockMode.ROAMING, true), idle, running))

    @Test
    fun `replay pauses`() = assertEquals(PauseAction.PauseReplay, pauseActionFor(active(MockMode.ROUTE_REPLAY), idle, running))

    @Test
    fun `paused replay does nothing`() =
        assertEquals(PauseAction.None, pauseActionFor(active(MockMode.ROUTE_REPLAY), idle, MockLocationState.PAUSED))

    @Test
    fun `replay fetch pauses the replay before the mode is ROUTE_REPLAY`() =
        assertEquals(PauseAction.PauseReplay, pauseActionFor(MovementState.Manual(MockMode.TELEPORT), RoadRouteFetch.ReplayFetch, running))
}
