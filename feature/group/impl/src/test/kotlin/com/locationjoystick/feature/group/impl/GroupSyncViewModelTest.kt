package com.locationjoystick.feature.group.impl

import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.location.FollowerSyncClient
import com.locationjoystick.core.location.LeaderSyncServer
import com.locationjoystick.core.model.GroupInvite
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.GroupState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSyncViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var groupRepository: GroupRepository
    private lateinit var groupNsdManager: NsdCodeManager
    private lateinit var leaderSyncServer: LeaderSyncServer
    private lateinit var followerSyncClient: FollowerSyncClient

    private lateinit var groupStateFlow: MutableStateFlow<GroupState>
    private lateinit var pendingInviteFlow: MutableSharedFlow<GroupInvite>
    private lateinit var leaderFollowerCount: MutableStateFlow<Int>
    private lateinit var followerFollowerCount: MutableStateFlow<Int>

    private lateinit var viewModel: GroupSyncViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers { fakeUri(firstArg()) }

        context = mockk(relaxed = true)
        groupRepository = mockk(relaxed = true)
        groupNsdManager = mockk()
        leaderSyncServer = mockk(relaxed = true)
        followerSyncClient = mockk(relaxed = true)

        groupStateFlow = MutableStateFlow(GroupState())
        pendingInviteFlow = MutableSharedFlow(replay = 1)
        leaderFollowerCount = MutableStateFlow(0)
        followerFollowerCount = MutableStateFlow(0)

        every { groupRepository.groupState } returns groupStateFlow
        every { groupRepository.pendingGroupInvite } returns pendingInviteFlow
        every { leaderSyncServer.followerCount } returns leaderFollowerCount
        every { followerSyncClient.followerCount } returns followerFollowerCount

        viewModel =
            GroupSyncViewModel(
                context = context,
                groupRepository = groupRepository,
                groupNsdManager = groupNsdManager,
                leaderSyncServer = leaderSyncServer,
                followerSyncClient = followerSyncClient,
            )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
    }

    /** android.net.Uri.parse() returns null under the unit-test stub jar — fake it with a real java.net.URI. */
    private fun fakeUri(url: String): Uri {
        val javaUri = java.net.URI(url)
        val params =
            (javaUri.query ?: "")
                .split("&")
                .filter { it.isNotEmpty() }
                .associate {
                    val (key, value) = it.split("=", limit = 2)
                    key to value
                }
        val uri: Uri = mockk()
        every { uri.scheme } returns javaUri.scheme
        every { uri.host } returns javaUri.host
        every { uri.getQueryParameter(any()) } answers { params[firstArg<String>()] }
        return uri
    }

    @Test
    fun `groupState mirrors repository state`() =
        runTest {
            groupStateFlow.value = GroupState(role = GroupRole.LEADER, groupId = "abc")

            viewModel.groupState.test {
                assertEquals(GroupRole.LEADER, awaitItem().role)
            }
        }

    @Test
    fun `createGroup starts the service`() =
        runTest {
            viewModel.createGroup()

            verify { context.startService(any()) }
        }

    @Test
    fun `joinByCode rejects wrong length without discovery`() =
        runTest {
            viewModel.joinByCode("AB")

            assertEquals("Code must be 6 characters", viewModel.errorMessage.value)
            coVerify(exactly = 0) { groupNsdManager.discoverByCode(any()) }
        }

    @Test
    fun `joinByCode joins group on successful discovery`() =
        runTest {
            coEvery { groupNsdManager.discoverByCode("ABC123") } returns ("10.0.0.5" to 4000)

            viewModel.joinByCode("abc123")

            coVerify { groupRepository.joinGroup(GroupInvite(host = "10.0.0.5", port = 4000, groupId = "ABC123")) }
            coVerify { groupRepository.consumeGroupInvite() }
            assertEquals(false, viewModel.isDiscovering.value)
        }

    @Test
    fun `joinByCode sets error when discovery finds nothing`() =
        runTest {
            coEvery { groupNsdManager.discoverByCode("ABC123") } returns null

            viewModel.joinByCode("ABC123")

            assertEquals("No group found for code ABC123", viewModel.errorMessage.value)
            coVerify(exactly = 0) { groupRepository.joinGroup(any()) }
        }

    @Test
    fun `joinViaScannedUrl joins group for a valid url`() =
        runTest {
            viewModel.joinViaScannedUrl("locationjoystick://group?host=1.2.3.4&port=9000&id=ZZZZZZ")

            coVerify {
                groupRepository.joinGroup(GroupInvite(host = "1.2.3.4", port = 9000, groupId = "ZZZZZZ"))
            }
        }

    @Test
    fun `joinViaScannedUrl sets error for an invalid url`() =
        runTest {
            viewModel.joinViaScannedUrl("https://example.com/not-a-group-link")

            assertEquals("Invalid group QR code", viewModel.errorMessage.value)
            coVerify(exactly = 0) { groupRepository.joinGroup(any()) }
        }

    @Test
    fun `handlePendingInvite exits leader role before joining`() =
        runTest {
            groupStateFlow.value = GroupState(role = GroupRole.LEADER, groupId = "old")

            viewModel.joinViaScannedUrl("locationjoystick://group?host=1.2.3.4&port=9000&id=ZZZZZZ")

            verify { context.startService(any()) }
            coVerify { groupRepository.joinGroup(any()) }
        }

    @Test
    fun `pendingGroupInvite from repository is consumed automatically`() =
        runTest {
            val invite = GroupInvite(host = "h", port = 1, groupId = "g")
            pendingInviteFlow.tryEmit(invite)

            coVerify { groupRepository.joinGroup(invite) }
            coVerify { groupRepository.consumeGroupInvite() }
        }

    @Test
    fun `setFollowerModeEnabled is a no-op when role is not FOLLOWER`() =
        runTest {
            groupStateFlow.value = GroupState(role = GroupRole.NONE)

            viewModel.setFollowerModeEnabled(true)

            coVerify(exactly = 0) { groupRepository.setFollowerModeEnabled(any()) }
            verify(exactly = 0) { context.startService(any()) }
        }

    @Test
    fun `setFollowerModeEnabled true persists the flag and starts the service`() =
        runTest {
            groupStateFlow.value =
                GroupState(role = GroupRole.FOLLOWER, groupId = "g", leaderHost = "h", leaderPort = 7)

            viewModel.setFollowerModeEnabled(true)

            coVerify { groupRepository.setFollowerModeEnabled(true) }
            verify { context.startService(any()) }
        }

    @Test
    fun `setFollowerModeEnabled false persists the flag and starts the service`() =
        runTest {
            groupStateFlow.value =
                GroupState(role = GroupRole.FOLLOWER, groupId = "g", leaderHost = "h", leaderPort = 7)

            viewModel.setFollowerModeEnabled(false)

            coVerify { groupRepository.setFollowerModeEnabled(false) }
            verify { context.startService(any()) }
        }

    @Test
    fun `setSharingEnabled delegates to repository`() =
        runTest {
            viewModel.setSharingEnabled(true)

            coVerify { groupRepository.setSharingEnabled(true) }
        }

    @Test
    fun `leaveGroup as LEADER exits leader and clears state`() =
        runTest {
            groupStateFlow.value = GroupState(role = GroupRole.LEADER, groupId = "g")

            viewModel.leaveGroup()

            verify { context.startService(any()) }
            coVerify { groupRepository.leaveGroup() }
        }

    @Test
    fun `leaveGroup as FOLLOWER with follower mode on exits follower`() =
        runTest {
            groupStateFlow.value = GroupState(role = GroupRole.FOLLOWER, followerModeEnabled = true)

            viewModel.leaveGroup()

            verify { context.startService(any()) }
            coVerify { groupRepository.leaveGroup() }
        }

    @Test
    fun `leaveGroup as FOLLOWER with follower mode off skips service intent`() =
        runTest {
            groupStateFlow.value = GroupState(role = GroupRole.FOLLOWER, followerModeEnabled = false)

            viewModel.leaveGroup()

            verify(exactly = 0) { context.startService(any()) }
            coVerify { groupRepository.leaveGroup() }
        }

    @Test
    fun `leaveGroup as NONE skips service intent but still clears repository`() =
        runTest {
            groupStateFlow.value = GroupState(role = GroupRole.NONE)

            viewModel.leaveGroup()

            verify(exactly = 0) { context.startService(any()) }
            coVerify { groupRepository.leaveGroup() }
        }

    @Test
    fun `clearError resets error message to null`() =
        runTest {
            viewModel.joinByCode("AB")
            assertEquals("Code must be 6 characters", viewModel.errorMessage.value)

            viewModel.clearError()

            assertNull(viewModel.errorMessage.value)
        }

    @Test
    fun `followerCount follows leaderSyncServer when role is LEADER`() =
        runTest {
            groupStateFlow.value = GroupState(role = GroupRole.LEADER)

            viewModel.followerCount.test {
                assertEquals(0, awaitItem())
                leaderFollowerCount.value = 3
                assertEquals(3, awaitItem())
            }
        }

    @Test
    fun `followerCount follows followerSyncClient when role is FOLLOWER`() =
        runTest {
            groupStateFlow.value = GroupState(role = GroupRole.FOLLOWER)

            viewModel.followerCount.test {
                assertEquals(0, awaitItem())
                followerFollowerCount.value = 2
                assertEquals(2, awaitItem())
            }
        }

    @Test
    fun `followerCount is zero when role is NONE`() =
        runTest {
            groupStateFlow.value = GroupState(role = GroupRole.NONE)

            viewModel.followerCount.test {
                assertEquals(0, awaitItem())
            }
        }
}
