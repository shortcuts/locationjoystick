package com.locationjoystick.core.location

import com.locationjoystick.core.model.ActiveMode
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.LeaderActivity
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MovementState
import com.locationjoystick.core.model.RouteProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MapSharedStateMirrorTest {
    private val local = MapSharedState()

    @Test
    fun `null activity leaves the state unchanged`() {
        assertSame(local, local.withLeaderActivity(null))
    }

    @Test
    fun `playing route shows running replay with progress`() {
        val s = local.withLeaderActivity(LeaderActivity.Route(false, RouteProgress(3, 8)))
        assertEquals(MovementState.Running(ActiveMode.ROUTE_REPLAY), s.movement)
        assertEquals(MockLocationState.RUNNING, s.mockLocationState)
        assertEquals(RouteProgress(3, 8), s.routeProgress)
        assertTrue(s.mirroredLeader)
    }

    @Test
    fun `paused route shows paused replay`() {
        val s = local.withLeaderActivity(LeaderActivity.Route(true, null))
        assertEquals(MovementState.Paused(ActiveMode.ROUTE_REPLAY), s.movement)
        assertEquals(MockLocationState.PAUSED, s.mockLocationState)
        assertNull(s.routeProgress)
    }

    @Test
    fun `roaming shows running or paused roaming`() {
        assertEquals(MovementState.Running(ActiveMode.ROAMING), local.withLeaderActivity(LeaderActivity.Roaming(false)).movement)
        assertEquals(MovementState.Paused(ActiveMode.ROAMING), local.withLeaderActivity(LeaderActivity.Roaming(true)).movement)
    }

    @Test
    fun `walking shows the target`() {
        val s = local.withLeaderActivity(LeaderActivity.Walking(true, LatLng(1.0, 2.0)))
        assertEquals(MovementState.Paused(ActiveMode.WALK_TO), s.movement)
        assertEquals(LatLng(1.0, 2.0), s.walkTarget)
        assertTrue(s.mirroredLeader)
    }

    @Test
    fun `walking with unknown target keeps the walk mode`() {
        assertEquals(WalkMode.Idle, local.withLeaderActivity(LeaderActivity.Walking(false, null)).walkMode)
    }

    @Test
    fun `local state is not mirrored`() {
        assertFalse(local.mirroredLeader)
    }
}
