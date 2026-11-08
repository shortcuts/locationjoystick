package com.locationjoystick.core.data

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.locationjoystick.core.model.GroupInvite
import com.locationjoystick.core.model.GroupState
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.testing.FakePreferencesDataStore
import kotlinx.coroutines.flow.first
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
    fun `initial state is None`() =
        runTest {
            repository.groupState.test {
                assertEquals(GroupState.None, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `createGroup emits Leader state with correct fields`() =
        runTest {
            repository.createGroup(host = "192.168.1.1", port = 5000, groupId = "abc")
            repository.groupState.test {
                val state = awaitItem() as GroupState.Leader
                assertEquals("abc", state.groupId)
                assertEquals("192.168.1.1", state.host)
                assertEquals(5000, state.port)
                assertTrue(state.sharingEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `joinGroup emits Follower state with invite fields`() =
        runTest {
            val invite = GroupInvite(host = "10.0.0.5", port = 4001, groupId = "xyz")
            repository.joinGroup(invite)
            repository.groupState.test {
                val state = awaitItem() as GroupState.Follower
                assertEquals("xyz", state.groupId)
                assertEquals("10.0.0.5", state.host)
                assertEquals(4001, state.port)
                assertTrue(state.followerModeEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `leaveGroup clears all keys — state becomes None`() =
        runTest {
            repository.createGroup(host = "192.168.1.1", port = 5000, groupId = "abc")
            repository.leaveGroup()
            repository.groupState.test {
                assertEquals(GroupState.None, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `followLeaderTeleports defaults true, persists false, and resets after leaving`() =
        runTest {
            repository.joinGroup(GroupInvite("h", 1, "id"))
            assertTrue((repository.groupState.first() as GroupState.Follower).followLeaderTeleports)
            repository.setFollowLeaderTeleports(false)
            assertFalse((repository.groupState.first() as GroupState.Follower).followLeaderTeleports)
            repository.leaveGroup()
            repository.joinGroup(GroupInvite("h", 1, "id"))
            assertTrue((repository.groupState.first() as GroupState.Follower).followLeaderTeleports)
        }

    @Test
    fun `leader role with no host decodes to None`() =
        runTest {
            repository.createGroup(host = "192.168.1.1", port = 5000, groupId = "abc")
            fakeDataStore.updateData { it.toMutablePreferences().apply { remove(stringPreferencesKey("group_leader_host")) } }
            repository.groupState.test {
                assertEquals(GroupState.None, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `follower role with no port decodes to None`() =
        runTest {
            repository.joinGroup(GroupInvite(host = "10.0.0.5", port = 4001, groupId = "xyz"))
            fakeDataStore.updateData { it.toMutablePreferences().apply { remove(intPreferencesKey("group_leader_port")) } }
            repository.groupState.test {
                assertEquals(GroupState.None, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `none role with stale host decodes to None`() =
        runTest {
            repository.createGroup(host = "192.168.1.1", port = 5000, groupId = "abc")
            fakeDataStore.writeRaw("group_role", "NONE")
            repository.groupState.test {
                assertEquals(GroupState.None, awaitItem())
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
    fun `setFollowerModeEnabled false turns off following`() =
        runTest {
            repository.joinGroup(GroupInvite("h", 1, "id"))
            repository.setFollowerModeEnabled(false)
            repository.groupState.test {
                val state = awaitItem() as GroupState.Follower
                assertFalse(state.followerModeEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setSharingEnabled updates sharingEnabled`() =
        runTest {
            repository.createGroup("h", 1, "id")
            repository.setSharingEnabled(false)
            repository.groupState.test {
                val state = awaitItem() as GroupState.Leader
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
                assertEquals(GroupState.None, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `api key is 32 alnum chars, stable, regenerable and survives leaveGroup`() =
        runTest {
            val key = repository.getOrCreateApiKey()
            assertTrue(Regex("[A-Za-z0-9]{32}").matches(key))
            assertEquals(key, repository.getOrCreateApiKey())
            repository.createGroup("h", 1, "abc")
            repository.leaveGroup()
            assertEquals(key, repository.getOrCreateApiKey())
            val fresh = repository.regenerateApiKey()
            assertTrue(fresh != key)
            assertEquals(fresh, repository.getOrCreateApiKey())
        }
}
