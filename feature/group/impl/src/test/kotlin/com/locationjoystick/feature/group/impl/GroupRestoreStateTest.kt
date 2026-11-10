package com.locationjoystick.feature.group.impl

import com.locationjoystick.core.model.GroupRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupRestoreStateTest {
    private fun RestoreState.on(vararg events: RestoreEvent) = events.fold(this) { s, e -> s.reduce(e) }

    @Test
    fun `leader role starts pending then done after the check`() {
        val s = RestoreState.Idle.on(RestoreEvent.RoleChanged(GroupRole.LEADER))
        assertEquals(RestoreState.LeaderPending(), s)
        assertEquals(RestoreState.LeaderDone(null), s.reduce(RestoreEvent.LeaderChecked))
    }

    @Test
    fun `follower walks pending, existence checked, done`() {
        val s = RestoreState.Idle.on(RestoreEvent.RoleChanged(GroupRole.FOLLOWER))
        assertEquals(RestoreState.FollowerPending, s)
        assertEquals(RestoreState.FollowerExistenceChecked, s.reduce(RestoreEvent.ExistenceChecked))
        assertEquals(RestoreState.FollowerDone, s.on(RestoreEvent.ExistenceChecked, RestoreEvent.FollowerRestoreChecked))
    }

    @Test
    fun `api key is carried by the leader states and survives the check`() {
        val s = RestoreState.Idle.on(RestoreEvent.RoleChanged(GroupRole.LEADER), RestoreEvent.ApiKeyLoaded("k"), RestoreEvent.LeaderChecked)
        assertEquals(RestoreState.LeaderDone("k"), s)
        assertEquals("k", s.apiKey)
        assertEquals("k2", s.reduce(RestoreEvent.ApiKeyLoaded("k2")).apiKey)
    }

    @Test
    fun `same role again keeps progress`() {
        val done = RestoreState.LeaderDone("k")
        assertEquals(done, done.reduce(RestoreEvent.RoleChanged(GroupRole.LEADER)))
        assertEquals(RestoreState.FollowerDone, RestoreState.FollowerDone.reduce(RestoreEvent.RoleChanged(GroupRole.FOLLOWER)))
    }

    @Test
    fun `leaving the role resets progress and drops the key`() {
        val idle = RestoreState.LeaderDone("k").reduce(RestoreEvent.RoleChanged(GroupRole.NONE))
        assertEquals(RestoreState.Idle, idle)
        assertNull(idle.apiKey)
        assertEquals(RestoreState.LeaderPending(), idle.reduce(RestoreEvent.RoleChanged(GroupRole.LEADER)))
        assertEquals(
            RestoreState.FollowerPending,
            RestoreState.FollowerDone.reduce(RestoreEvent.RoleChanged(GroupRole.NONE)).reduce(RestoreEvent.RoleChanged(GroupRole.FOLLOWER)),
        )
        assertEquals(RestoreState.FollowerPending, RestoreState.LeaderDone("k").reduce(RestoreEvent.RoleChanged(GroupRole.FOLLOWER)))
    }

    @Test
    fun `rejected events leave the state unchanged`() {
        assertEquals(RestoreState.Idle, RestoreState.Idle.reduce(RestoreEvent.LeaderChecked))
        assertEquals(RestoreState.Idle, RestoreState.Idle.reduce(RestoreEvent.ApiKeyLoaded("k")))
        assertEquals(RestoreState.FollowerPending, RestoreState.FollowerPending.reduce(RestoreEvent.FollowerRestoreChecked))
        assertEquals(RestoreState.LeaderPending(), RestoreState.LeaderPending().reduce(RestoreEvent.ExistenceChecked))
        assertEquals(RestoreState.FollowerDone, RestoreState.FollowerDone.reduce(RestoreEvent.ExistenceChecked))
        assertEquals(RestoreState.LeaderDone("k"), RestoreState.LeaderDone("k").reduce(RestoreEvent.LeaderChecked))
    }
}
