package com.locationjoystick.core.data

import app.cash.turbine.test
import com.locationjoystick.core.model.GroupInvite
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.testing.FakePreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GroupRepositoryTest {
    private lateinit var fakeDataStore: FakePreferencesDataStore
    private lateinit var repository: GroupRepository

    @Before
    fun setUp() {
        fakeDataStore = FakePreferencesDataStore()
        repository = GroupRepository(fakeDataStore)
    }

    @Test
    fun `initial state has role NONE and nulls`() =
        runTest {
            repository.groupState.test {
                val state = awaitItem()
                assertEquals(GroupRole.NONE, state.role)
                assertNull(state.groupId)
                assertNull(state.leaderHost)
                assertNull(state.leaderPort)
                assertFalse(state.followerModeEnabled)
                assertFalse(state.sharingEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `createGroup emits LEADER state with correct fields`() =
        runTest {
            repository.createGroup(host = "192.168.1.1", port = 5000, groupId = "abc")
            repository.groupState.test {
                val state = awaitItem()
                assertEquals(GroupRole.LEADER, state.role)
                assertEquals("abc", state.groupId)
                assertEquals("192.168.1.1", state.leaderHost)
                assertEquals(5000, state.leaderPort)
                assertTrue(state.sharingEnabled)
                assertFalse(state.followerModeEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `joinGroup emits FOLLOWER state with invite fields`() =
        runTest {
            val invite = GroupInvite(host = "10.0.0.5", port = 4001, groupId = "xyz")
            repository.joinGroup(invite)
            repository.groupState.test {
                val state = awaitItem()
                assertEquals(GroupRole.FOLLOWER, state.role)
                assertEquals("xyz", state.groupId)
                assertEquals("10.0.0.5", state.leaderHost)
                assertEquals(4001, state.leaderPort)
                assertFalse(state.followerModeEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `leaveGroup clears all keys — role becomes NONE`() =
        runTest {
            repository.createGroup(host = "192.168.1.1", port = 5000, groupId = "abc")
            repository.leaveGroup()
            repository.groupState.test {
                val state = awaitItem()
                assertEquals(GroupRole.NONE, state.role)
                assertNull(state.groupId)
                assertNull(state.leaderHost)
                assertNull(state.leaderPort)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setLeaderPosition updates leaderPosition flow`() =
        runTest {
            repository.leaderPosition.test {
                assertNull(awaitItem())
                repository.setLeaderPosition(LatLng(1.0, 2.0))
                val updated = awaitItem()
                assertEquals(1.0, updated!!.latitude, 0.0)
                assertEquals(2.0, updated.longitude, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `leaveGroup clears leaderPosition`() =
        runTest {
            repository.setLeaderPosition(LatLng(1.0, 2.0))
            repository.leaveGroup()
            repository.leaderPosition.test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setFollowerModeEnabled updates followerModeEnabled`() =
        runTest {
            repository.joinGroup(GroupInvite("h", 1, "id"))
            repository.setFollowerModeEnabled(true)
            repository.groupState.test {
                val state = awaitItem()
                assertTrue(state.followerModeEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setSharingEnabled updates sharingEnabled`() =
        runTest {
            repository.createGroup("h", 1, "id")
            repository.setSharingEnabled(false)
            repository.groupState.test {
                val state = awaitItem()
                assertFalse(state.sharingEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setPendingGroupInvite delivers invite to subscriber`() =
        runTest {
            val invite = GroupInvite("h", 9, "g")
            repository.pendingGroupInvite.test {
                repository.setPendingGroupInvite(invite)
                assertEquals(invite, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `consumeGroupInvite clears replay — late subscriber receives nothing`() =
        runTest {
            repository.setPendingGroupInvite(GroupInvite("h", 9, "g"))
            repository.consumeGroupInvite()
            repository.pendingGroupInvite.test {
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emitTeleportUnavailable delivers event to subscriber`() =
        runTest {
            repository.teleportUnavailableEvent.test {
                repository.emitTeleportUnavailable()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unknown role string falls back to NONE`() =
        runTest {
            // Write a garbage role directly via the fake store
            fakeDataStore.writeRaw("group_role", "INVALID_ROLE")
            repository.groupState.test {
                val state = awaitItem()
                assertEquals(GroupRole.NONE, state.role)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
