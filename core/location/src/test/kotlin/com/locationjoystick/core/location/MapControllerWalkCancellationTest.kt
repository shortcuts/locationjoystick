package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RealLocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.data.WalkToEngine
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.GroupState
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SavedItemSortMode
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoutingErrorReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression test for the bug where starting a new walk-to while an ephemeral replay
 * (built via "add next point") was active would not cancel the replay, causing the
 * previous route to continue instead of starting the new walk.
 *
 * Scenario: walkTo(target1) → addEphemeralWaypoint ×5 → walkTo(target2)
 * Expected: ephemeral replay cancelled, new walk to target2 starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapControllerWalkCancellationTest {
    private val walkProfile = SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = 1.4)

    @Before
    fun setUp() {
        mockkObject(MockLocationIntentBuilder)
        every { MockLocationIntentBuilder.startEphemeralReplay(any(), any(), any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.appendWaypoint(any(), any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.cancelRouteReplay(any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.updatePosition(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.pauseRouteReplay(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(MockLocationIntentBuilder)
    }

    @Test
    fun `walkTo cancels active ephemeral replay and starts new walk`() =
        runTest(UnconfinedTestDispatcher()) {
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    // resolveRoute with followRoads=false returns a straight-line pair — mirror
                    // the real implementation so addWaypoint produces a valid waypoint list.
                    coEvery { it.resolveRoute(any(), any(), any(), any(), any()) } answers {
                        listOf(secondArg<LatLng>(), thirdArg<LatLng>())
                    }
                }
            val harness = buildHarness(backgroundScope, osrmClient)
            val locationRepository = harness.locationRepository
            val ephemeralController = harness.ephemeralController
            val mapController = harness.mapController

            // Step 1: start walk-to (not via roads)
            val start = LatLng(48.8566, 2.3522)
            val target1 = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(start)
            mapController.walkTo(target1)

            assertEquals(target1, locationRepository.walkTarget.value)
            assertEquals(MockMode.WALK_TO, locationRepository.currentMode.value)

            // Step 2: tap "add next point" 5 times — transitions walk → ephemeral replay
            val nextPoints = (1..5).map { i -> LatLng(48.9000 + i * 0.01, 2.3522) }
            for (point in nextPoints) {
                mapController.addEphemeralWaypoint(point)
            }
            assertTrue(
                "Ephemeral waypoints should be populated after 5 add-next-point taps",
                ephemeralController.pendingWaypoints.value.isNotEmpty(),
            )
            assertTrue(
                "walkMode should be EphemeralReplay",
                mapController.sharedState.value.walkMode is WalkMode.EphemeralReplay,
            )

            // Step 3: pick a new destination and tap "Walk here"
            val target2 = LatLng(48.8700, 2.3000)
            mapController.walkTo(target2)

            // Step 4: ephemeral replay must be fully cancelled; new walk to target2 must be active
            assertTrue(
                "Ephemeral waypoints must be cleared when new walk-to starts",
                ephemeralController.pendingWaypoints.value.isEmpty(),
            )
            assertEquals(
                "Walk target must be the new destination",
                target2,
                locationRepository.walkTarget.value,
            )
            assertEquals(
                "Mode must be WALK_TO",
                MockMode.WALK_TO,
                locationRepository.currentMode.value,
            )

            val walkMode = mapController.sharedState.value.walkMode
            assertTrue(
                "walkMode must be the new Walking state after walkTo(target2), not left over " +
                    "from the cancelled ephemeral replay",
                walkMode is WalkMode.Walking && walkMode.target == target2,
            )
        }

    // Regression for issue #99: a teleport from a screen that bypasses MapController (Favorites,
    // Routes, Capture jump) only cancels via WalkCoordinator. A walk-via-roads whose routing
    // result lands afterwards must not start walking from the teleport spot toward the old target.
    @Test
    fun `teleport while walk-via-roads is routing drops the late route`() =
        runTest(UnconfinedTestDispatcher()) {
            val routeResult = CompletableDeferred<Result<List<LatLng>>>()
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.getRoute(any(), any()) } coAnswers { routeResult.await() }
                }
            val harness = buildHarness(backgroundScope, osrmClient)
            val start = LatLng(48.8566, 2.3522)
            val targetA = LatLng(48.9000, 2.3522)
            val teleportB = LatLng(48.8000, 2.3000)
            harness.locationRepository.setPositionInternal(start)

            harness.mapController.walkViaRoads(targetA)
            // What TeleportUseCase.execute(resetMovement = true) does before the jump.
            harness.walkCoordinator.cancel()
            harness.locationRepository.setPositionInternal(teleportB)
            routeResult.complete(Result.success(listOf(start, targetA)))

            assertNull("Late route must not revive the walk", harness.locationRepository.walkTarget.value)
            assertNotEquals(MockMode.WALK_TO, harness.locationRepository.currentMode.value)
            assertFalse(harness.mapController.sharedState.value.walkMode is WalkMode.Walking)
            assertEquals(teleportB, harness.locationRepository.currentPosition.value)
        }

    @Test
    fun `teleport while walk-via-roads is routing drops the straight-line fallback`() =
        runTest(UnconfinedTestDispatcher()) {
            val routeResult = CompletableDeferred<Result<List<LatLng>>>()
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.getRoute(any(), any()) } coAnswers { routeResult.await() }
                }
            val harness = buildHarness(backgroundScope, osrmClient)
            harness.locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))

            harness.mapController.walkViaRoads(LatLng(48.9000, 2.3522))
            harness.walkCoordinator.cancel()
            routeResult.complete(Result.failure(IllegalStateException("routing down")))

            assertNull(harness.locationRepository.walkTarget.value)
            assertNotEquals(MockMode.WALK_TO, harness.locationRepository.currentMode.value)
            assertFalse(harness.mapController.sharedState.value.walkMode is WalkMode.Walking)
        }

    @Test
    fun `walkTo during roaming stops roaming before the walk starts`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = buildHarness(backgroundScope, mockk(relaxed = true))
            harness.locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))
            harness.setRoamingActive()
            val target = LatLng(48.9000, 2.3522)

            harness.mapController.walkTo(target)

            coVerify(exactly = 1) { harness.roamingRepository.stopRoaming() }
            assertEquals(MockMode.WALK_TO, harness.locationRepository.currentMode.value)
            assertEquals(target, harness.locationRepository.walkTarget.value)
        }

    @Test
    fun `walkViaRoads during roaming stops roaming before routing and then walks`() =
        runTest(UnconfinedTestDispatcher()) {
            val routeResult = CompletableDeferred<Result<List<LatLng>>>()
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.getRoute(any(), any()) } coAnswers { routeResult.await() }
                }
            val harness = buildHarness(backgroundScope, osrmClient)
            val start = LatLng(48.8566, 2.3522)
            val target = LatLng(48.9000, 2.3522)
            harness.locationRepository.setPositionInternal(start)
            harness.setRoamingActive()

            harness.mapController.walkViaRoads(target)

            coVerify(exactly = 1) { harness.roamingRepository.stopRoaming() }
            assertEquals(MockMode.TELEPORT, harness.locationRepository.currentMode.value)
            routeResult.complete(Result.success(listOf(start, target)))
            assertEquals(MockMode.WALK_TO, harness.locationRepository.currentMode.value)
            assertEquals(target, harness.locationRepository.walkTarget.value)
        }

    @Test
    fun `walkTo without roaming never stops roaming`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = buildHarness(backgroundScope, mockk(relaxed = true))
            harness.locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))

            harness.mapController.walkTo(LatLng(48.9000, 2.3522))

            coVerify(exactly = 0) { harness.roamingRepository.stopRoaming() }
            assertEquals(MockMode.WALK_TO, harness.locationRepository.currentMode.value)
        }

    @Test
    fun `joystick takeover in follower mode turns Follow leader off once`() =
        runTest(UnconfinedTestDispatcher()) {
            every { MockLocationIntentBuilder.exitFollower(any()) } returns mockk(relaxed = true)
            val gate = CompletableDeferred<Unit>()
            val harness = buildHarness(backgroundScope, mockk(relaxed = true))
            coEvery { harness.groupRepository.setFollowerModeEnabled(false) } coAnswers { gate.await() }
            harness.locationRepository.setMockMode(MockMode.FOLLOWER)

            harness.mapController.pauseAutomatedMovement()
            harness.mapController.pauseAutomatedMovement()
            gate.complete(Unit)

            coVerify(exactly = 1) { harness.groupRepository.setFollowerModeEnabled(false) }
            verify(exactly = 1) { harness.context.startService(any()) }
        }

    @Test
    fun `follower takeover resumes Follow leader after idle window, not while still moving`() =
        runTest(UnconfinedTestDispatcher()) {
            every { MockLocationIntentBuilder.exitFollower(any()) } returns mockk(relaxed = true)
            every { MockLocationIntentBuilder.resumeFollower(any()) } returns mockk(relaxed = true)
            val harness = buildHarness(backgroundScope, mockk(relaxed = true))
            every { harness.groupRepository.groupState } returns
                flowOf(GroupState(role = GroupRole.FOLLOWER, groupId = "AB3X9K"))
            val repo = harness.locationRepository
            repo.setMockMode(MockMode.FOLLOWER)
            repo.setPositionInternal(LatLng(48.8566, 2.3522))
            harness.mapController.pauseAutomatedMovement()
            // What the service does on ACTION_EXIT_FOLLOWER.
            repo.setMockMode(MockMode.TELEPORT)
            repo.startSpoofing()

            advanceTimeBy(AppConstants.SyncConstants.FOLLOWER_RESUME_IDLE_MS - 1_000)
            repo.setPositionInternal(LatLng(48.8570, 2.3522))
            advanceTimeBy(AppConstants.SyncConstants.FOLLOWER_RESUME_IDLE_MS - 1_000)
            runCurrent()
            coVerify(exactly = 0) { harness.groupRepository.setFollowerModeEnabled(true) }

            advanceTimeBy(2_000)
            runCurrent()
            coVerify(exactly = 1) { harness.groupRepository.setFollowerModeEnabled(true) }
            verify(exactly = 1) { MockLocationIntentBuilder.resumeFollower(any()) }
        }

    @Test
    fun `follower that teleports or plays a route resumes Follow leader once idle again`() =
        runTest(UnconfinedTestDispatcher()) {
            every { MockLocationIntentBuilder.resumeFollower(any()) } returns mockk(relaxed = true)
            val harness = buildHarness(backgroundScope, mockk(relaxed = true))
            every { harness.groupRepository.groupState } returns
                flowOf(GroupState(role = GroupRole.FOLLOWER, groupId = "AB3X9K", followerModeEnabled = true))
            val repo = harness.locationRepository
            repo.setMockMode(MockMode.FOLLOWER)
            repo.startSpoofing()
            repo.setPositionInternal(LatLng(48.8566, 2.3522))

            // Favorite teleport / route start: the flag stays on, only the mode changes.
            repo.setMockMode(MockMode.ROUTE_REPLAY)
            advanceTimeBy(AppConstants.SyncConstants.FOLLOWER_RESUME_IDLE_MS * 3)
            runCurrent()
            coVerify(exactly = 0) { harness.groupRepository.setFollowerModeEnabled(true) }

            // Route done: nothing moves for one window, then following resumes.
            repo.setMockMode(MockMode.TELEPORT)
            advanceTimeBy(AppConstants.SyncConstants.FOLLOWER_RESUME_IDLE_MS * 2 + 1_000)
            runCurrent()
            coVerify(exactly = 1) { harness.groupRepository.setFollowerModeEnabled(true) }
            verify(exactly = 1) { MockLocationIntentBuilder.resumeFollower(any()) }
        }

    @Test
    fun `joystick pauses a pending road walk and resume walks from the manual position`() =
        runTest(UnconfinedTestDispatcher()) {
            val routeResult = CompletableDeferred<Result<List<LatLng>>>()
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.getRoute(any(), any()) } coAnswers { routeResult.await() }
                }
            val harness = buildHarness(backgroundScope, osrmClient)
            val start = LatLng(48.8566, 2.3522)
            harness.locationRepository.setPositionInternal(start)

            harness.mapController.walkViaRoads(LatLng(48.9000, 2.3522))
            harness.mapController.pauseAutomatedMovement()
            harness.mapController.pauseAutomatedMovement()
            val manualPosition = LatLng(48.8600, 2.3530)
            harness.locationRepository.setPositionInternal(manualPosition)
            routeResult.complete(Result.success(listOf(start, LatLng(48.9000, 2.3522))))

            assertEquals(LatLng(48.9000, 2.3522), harness.locationRepository.walkTarget.value)
            assertEquals(MockMode.WALK_TO, harness.locationRepository.currentMode.value)
            assertTrue(harness.locationRepository.isWalkPaused.value)
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(manualPosition, harness.locationRepository.currentPosition.value)
            harness.mapController.resumeWalk()
            advanceTimeBy(1_000)
            runCurrent()
            assertNotEquals(manualPosition, harness.locationRepository.currentPosition.value)
            coVerify(exactly = 0) { harness.teleportUseCase.stopAutomatedMovement() }
        }

    @Test
    fun `manual pause retains destination and repeated map walks keep moving`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = buildHarness(backgroundScope, mockk(relaxed = true))
            val repo = harness.locationRepository
            repo.setPositionInternal(LatLng(48.8566, 2.3522))
            val targets = listOf(LatLng(48.9000, 2.3522), LatLng(48.9100, 2.3600))
            repeat(6) { index ->
                val target = targets[index / 3]
                harness.mapController.walkTo(target)
                val generation = harness.walkCoordinator.currentGeneration()
                harness.mapController.pauseAutomatedMovement()
                harness.mapController.pauseAutomatedMovement()
                assertEquals(generation, harness.walkCoordinator.currentGeneration())
                assertEquals(target, repo.walkTarget.value)
                assertTrue(repo.isWalkPaused.value)
                assertEquals(MockMode.WALK_TO, repo.currentMode.value)

                val manualPosition = LatLng(48.8600 + index * 0.001, 2.3500)
                repo.setPositionInternal(manualPosition)
                advanceTimeBy(2_000)
                runCurrent()
                assertEquals(manualPosition, repo.currentPosition.value)
                harness.mapController.resumeWalk()
                advanceTimeBy(1_000)
                runCurrent()
                assertNotEquals(manualPosition, repo.currentPosition.value)
                assertEquals(target, repo.walkTarget.value)
                assertFalse(repo.isWalkPaused.value)
            }
            coVerify(exactly = 0) { harness.teleportUseCase.stopAutomatedMovement() }
        }

    @Test
    fun `new map destination replaces a paused walk for repeated same and different targets`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = buildHarness(backgroundScope, mockk(relaxed = true))
            val repo = harness.locationRepository
            repo.setPositionInternal(LatLng(48.8566, 2.3522))
            val targets = listOf(LatLng(48.9000, 2.3522), LatLng(48.9100, 2.3600))
            harness.mapController.walkTo(targets.first())

            repeat(6) { index ->
                harness.mapController.pauseAutomatedMovement()
                val manualPosition = LatLng(48.8600 + index * 0.001, 2.3500)
                repo.setPositionInternal(manualPosition)
                advanceTimeBy(1_000)
                runCurrent()
                assertTrue(repo.isWalkPaused.value)
                assertEquals(manualPosition, repo.currentPosition.value)

                val target = targets[(index / 2) % targets.size]
                harness.mapController.walkTo(target)
                runCurrent()
                assertFalse(repo.isWalkPaused.value)
                assertEquals(target, repo.walkTarget.value)
                assertEquals(MockMode.WALK_TO, repo.currentMode.value)
                advanceTimeBy(1_000)
                runCurrent()
                assertNotEquals(manualPosition, repo.currentPosition.value)
                assertEquals(target, repo.walkTarget.value)
            }
        }

    @Test
    fun `repeated joystick input requests route pause once without clearing progress`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = buildHarness(backgroundScope, mockk(relaxed = true))
            val repo = harness.locationRepository
            repo.setMockMode(MockMode.ROUTE_REPLAY)
            repo.startSpoofing()
            repo.setActiveRouteId("saved-route")

            repeat(3) { harness.mapController.pauseAutomatedMovement() }

            assertEquals(MockLocationState.PAUSED, repo.mockLocationState.value)
            assertEquals(MockMode.ROUTE_REPLAY, repo.currentMode.value)
            assertEquals("saved-route", repo.activeRouteId.value)
            verify(exactly = 1) { MockLocationIntentBuilder.pauseRouteReplay(any()) }
            coVerify(exactly = 0) { harness.teleportUseCase.stopAutomatedMovement() }
        }

    @Test
    fun `first add next point seeds replay from current position`() =
        runTest(UnconfinedTestDispatcher()) {
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.resolveRoute(any(), any(), any(), any(), any()) } answers {
                        listOf(secondArg<LatLng>(), thirdArg<LatLng>())
                    }
                }
            val harness = buildHarness(backgroundScope, osrmClient)
            val start = LatLng(48.8566, 2.3522)
            val mid = LatLng(48.8700, 2.3522)
            val target = LatLng(48.9000, 2.3522)
            val newPoint = LatLng(48.9100, 2.3522)
            harness.locationRepository.setPositionInternal(start)
            harness.mapController.walkTo(target)
            harness.locationRepository.setPositionInternal(mid)

            harness.mapController.addEphemeralWaypoint(newPoint)

            assertEquals(listOf(mid, target, newPoint), harness.ephemeralController.pendingWaypoints.value)
        }

    @Test
    fun `first add next point after walk via roads keeps remaining road shape`() =
        runTest(UnconfinedTestDispatcher()) {
            val a = LatLng(48.8566, 2.3522)
            val b = LatLng(48.8600, 2.3522)
            val c = LatLng(48.8700, 2.3522)
            val d = LatLng(48.8800, 2.3522)
            val newPoint = LatLng(48.8900, 2.3522)
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.getRoute(any(), any()) } returns Result.success(listOf(a, b, c, d))
                    coEvery { it.resolveRoute(any(), any(), any(), any(), any()) } answers {
                        listOf(secondArg<LatLng>(), thirdArg<LatLng>())
                    }
                }
            val harness = buildHarness(backgroundScope, osrmClient)
            val mid = LatLng(48.8650, 2.3522)
            harness.locationRepository.setPositionInternal(a)
            harness.mapController.walkViaRoads(d)
            harness.locationRepository.setPositionInternal(mid)

            harness.mapController.addEphemeralWaypoint(newPoint, followRoads = true)

            assertEquals(listOf(mid, c, d, newPoint), harness.ephemeralController.pendingWaypoints.value)
            coVerify(exactly = 1) { osrmClient.resolveRoute(any(), any(), any(), any(), any()) }
        }

    private class Harness(
        val locationRepository: LocationRepository,
        val walkCoordinator: WalkCoordinator,
        val ephemeralController: EphemeralReplayController,
        val mapController: MapController,
        val teleportUseCase: TeleportUseCase,
        val groupRepository: GroupRepository,
        val context: Context,
        val roamingRepository: RoamingRepository,
        val isRoaming: MutableStateFlow<Boolean>,
    ) {
        fun setRoamingActive() {
            locationRepository.setMockMode(MockMode.ROAMING)
            isRoaming.value = true
            coEvery { roamingRepository.stopRoaming() } answers {
                isRoaming.value = false
                locationRepository.setMockMode(MockMode.TELEPORT)
            }
        }
    }

    private fun buildHarness(
        scope: CoroutineScope,
        osrmClient: OsrmClient,
    ): Harness {
        val locationRepository = LocationRepository()
        val settingsRepository =
            mockk<SettingsRepository>(relaxed = true) {
                every { getActiveSpeedProfile() } returns flowOf(walkProfile)
                every { getRoutesSortMode() } returns flowOf(SavedItemSortMode.NEWEST_FIRST)
                every { getFavoritesSortMode() } returns flowOf(SavedItemSortMode.NEWEST_FIRST)
                every { getHomeFavoriteId() } returns flowOf(null)
                every { getSpeedUnit() } returns flowOf(SpeedUnit.KMH)
                every { getRecentSearches() } returns flowOf(emptyList())
                every { getRoamingDefaults() } returns flowOf(RoamingDefaults())
                every { getSettingsSnapshot() } returns emptyFlow()
                every { getRememberLastLocation() } returns flowOf(false)
            }
        val walkToEngine = WalkToEngine(settingsRepository, locationRepository)
        val walkCoordinator = WalkCoordinator(locationRepository, walkToEngine)
        val routingErrorReporter = RoutingErrorReporter(mockk<android.content.Context>(relaxed = true))
        val ephemeralController =
            EphemeralReplayController(
                locationRepository,
                settingsRepository,
                walkCoordinator,
                osrmClient,
                routingErrorReporter,
            )

        val context = mockk<Context>(relaxed = true)
        val isRoaming = MutableStateFlow(false)
        val isRoamingPaused = MutableStateFlow(false)
        val roamingRepository =
            mockk<RoamingRepository>(relaxed = true) {
                every { this@mockk.isRoaming } returns isRoaming
                every { this@mockk.isRoamingPaused } returns isRoamingPaused
            }
        val routeRepository = mockk<RouteRepository>(relaxed = true) { every { getRoutes() } returns emptyFlow() }
        val favoriteRepository =
            mockk<FavoriteRepository>(relaxed = true) { every { getFavorites() } returns flowOf(emptyList()) }
        val teleportUseCase =
            mockk<TeleportUseCase>(relaxed = true) { every { cooldownsFor(any()) } returns emptyFlow() }
        val startRouteReplayUseCase = mockk<StartRouteReplayUseCase>(relaxed = true)
        val groupRepository = mockk<GroupRepository>(relaxed = true)

        val mapController =
            MapController(
                context = context,
                locationRepository = locationRepository,
                routeRepository = routeRepository,
                favoriteRepository = favoriteRepository,
                settingsRepository = settingsRepository,
                roamingRepository = roamingRepository,
                walkCoordinator = walkCoordinator,
                teleportUseCase = teleportUseCase,
                serviceStarter = mockk(relaxed = true),
                realLocationRepository =
                    mockk<RealLocationRepository> {
                        every { lastKnownRealPosition() } returns null
                        every { hasFinePermission() } returns false
                    },
                startRouteReplayUseCase = startRouteReplayUseCase,
                ephemeralReplayController = ephemeralController,
                osrmClient = osrmClient,
                routingErrorReporter = routingErrorReporter,
                groupRepository = groupRepository,
                appScope = scope,
            )
        return Harness(
            locationRepository,
            walkCoordinator,
            ephemeralController,
            mapController,
            teleportUseCase,
            groupRepository,
            context,
            roamingRepository,
            isRoaming,
        )
    }
}
