package com.locationjoystick.core.location

import com.locationjoystick.core.data.RoadRouteFetch
import com.locationjoystick.core.model.ActiveMode
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.MovementState
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Test

class PauseActionTest {
    private val idle = RoadRouteFetch.Idle
    private val walkFetch = RoadRouteFetch.WalkFetch(Job())

    private fun active(
        mode: MockMode,
        paused: Boolean = false,
    ) = if (paused) MovementState.Paused(ActiveMode.valueOf(mode.name)) else MovementState.Running(ActiveMode.valueOf(mode.name))

    @Test
    fun `follower leaves follow leader`() =
        assertEquals(PauseAction.LeaveFollower, pauseActionFor(MovementState.Manual(MockMode.FOLLOWER), idle))

    @Test
    fun `joystick with nothing running does nothing`() =
        assertEquals(PauseAction.None, pauseActionFor(MovementState.Manual(MockMode.JOYSTICK), idle))

    @Test
    fun `walk pauses`() = assertEquals(PauseAction.PauseWalk, pauseActionFor(active(MockMode.WALK_TO), idle))

    @Test
    fun `paused walk does nothing`() = assertEquals(PauseAction.None, pauseActionFor(active(MockMode.WALK_TO, true), idle))

    @Test
    fun `walk fetch pauses the walk before the mode is WALK_TO`() =
        assertEquals(PauseAction.PauseWalk, pauseActionFor(MovementState.Manual(MockMode.TELEPORT), walkFetch))

    @Test
    fun `walk fetch already paused does nothing`() =
        assertEquals(
            PauseAction.None,
            pauseActionFor(MovementState.PausePending(MockMode.TELEPORT, ActiveMode.WALK_TO), walkFetch),
        )

    @Test
    fun `walk fetch outranks roaming`() = assertEquals(PauseAction.PauseWalk, pauseActionFor(active(MockMode.ROAMING), walkFetch))

    @Test
    fun `roaming pauses`() = assertEquals(PauseAction.PauseRoaming, pauseActionFor(active(MockMode.ROAMING), idle))

    @Test
    fun `paused roaming does nothing`() = assertEquals(PauseAction.None, pauseActionFor(active(MockMode.ROAMING, true), idle))

    @Test
    fun `replay pauses`() = assertEquals(PauseAction.PauseReplay, pauseActionFor(active(MockMode.ROUTE_REPLAY), idle))

    @Test
    fun `paused replay does nothing`() = assertEquals(PauseAction.None, pauseActionFor(active(MockMode.ROUTE_REPLAY, true), idle))

    @Test
    fun `replay fetch pauses the replay before the mode is ROUTE_REPLAY`() =
        assertEquals(PauseAction.PauseReplay, pauseActionFor(MovementState.Manual(MockMode.TELEPORT), RoadRouteFetch.ReplayFetch))

    @Test
    fun `replay fetch already paused does nothing`() =
        assertEquals(
            PauseAction.None,
            pauseActionFor(MovementState.PausePending(MockMode.TELEPORT, ActiveMode.ROUTE_REPLAY), RoadRouteFetch.ReplayFetch),
        )
}
