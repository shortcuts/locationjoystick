package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.buildPlantingReplayPath
import com.locationjoystick.core.common.util.calculateBearing
import com.locationjoystick.core.common.util.plantingRings
import com.locationjoystick.core.common.util.stitchRingsWithConnectorsAndBoundaries
import com.locationjoystick.core.common.util.stitchRingsWithoutConnectors
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.WalkToEngine
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteProgress
import com.locationjoystick.core.model.RouteStartConfig
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RouteReplayEngine
import com.locationjoystick.core.routing.RoutingErrorReporter
import com.locationjoystick.core.routing.TeleportRouteEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayOrchestratorTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val locationRepository = LocationRepository()
    private val routeRepository: RouteRepository = mockk(relaxed = true)
    private val roamingRepository: RoamingRepository = mockk(relaxed = true)
    private val routeReplayEngine: RouteReplayEngine = mockk(relaxed = true)
    private val teleportRouteEngine: TeleportRouteEngine = mockk(relaxed = true)
    private val walkToEngine: WalkToEngine = mockk(relaxed = true)
    private val osrmClient: OsrmClient = mockk(relaxed = true)
    private val routingErrorReporter: RoutingErrorReporter = mockk(relaxed = true)

    private val stateChanges = mutableListOf<MockLocationState>()
    private val speedChanges = mutableListOf<Float>()
    private var startUpdateLoopCalled = false
    private var teleportCount = 0
    private var plantingRadius = AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS

    private lateinit var orchestrator: ReplayOrchestrator

    @Before
    fun setup() {
        stateChanges.clear()
        speedChanges.clear()
        startUpdateLoopCalled = false
        teleportCount = 0
        plantingRadius = AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS
        orchestrator =
            ReplayOrchestrator(
                context = context,
                locationRepository = locationRepository,
                routeRepository = routeRepository,
                roamingRepository = roamingRepository,
                routeReplayEngine = routeReplayEngine,
                teleportRouteEngine = teleportRouteEngine,
                walkToEngine = walkToEngine,
                osrmClient = osrmClient,
                routingErrorReporter = routingErrorReporter,
                scope = kotlinx.coroutines.CoroutineScope(dispatcher),
                onStateChange = { stateChanges.add(it) },
                onPositionChange = { _, _ -> },
                onSpeedChange = { speedChanges.add(it) },
                pushLocationUpdate = { },
                startUpdateLoop = { startUpdateLoopCalled = true },
                onTeleport = { teleportCount++ },
                plantingRadiusMeters = { plantingRadius },
            )
    }

    @Test
    fun handlePause_pausesEngine_and_emitsState() {
        locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
        orchestrator.handlePause()

        verify { routeReplayEngine.pause() }
        assertEquals(MockLocationState.PAUSED, stateChanges.last())
        assertEquals(MockLocationState.PAUSED, locationRepository.mockLocationState.value)
    }

    @Test
    fun handlePause_whenIdleOrNewWalkOwnsMovement_doesNothing() {
        orchestrator.handlePause()
        locationRepository.setWalkTarget(LatLng(1.0, 1.0))
        locationRepository.startSpoofing()

        orchestrator.handlePause()

        verify(exactly = 0) { routeReplayEngine.pause() }
        assertTrue(stateChanges.isEmpty())
        assertEquals(MockMode.WALK_TO, locationRepository.currentMode.value)
        assertEquals(MockLocationState.RUNNING, locationRepository.mockLocationState.value)
        assertFalse(locationRepository.isWalkPaused.value)
    }

    @Test
    fun handlePause_duringPlanning_keepsRoutePausedAfterRoadResult_untilResume() =
        runTest {
            val planned = CompletableDeferred<List<LatLng>>()
            val first = LatLng(1.0, 1.0)
            val last = LatLng(2.0, 2.0)
            val route = Route("pending", "Pending", waypoints = listOf(Waypoint("a", first, 0), Waypoint("b", last, 1)))
            locationRepository.setPositionInternal(first)
            coEvery { routeRepository.getRouteWithWaypoints("pending") } returns flowOf(route)
            coEvery { osrmClient.resolveRoute(any(), first, last, true, any()) } coAnswers { planned.await() }

            orchestrator.handleStart("pending", 1.4, RouteStartConfig(followRoadsToStart = true, teleportToStart = true))
            // The joystick marks the repository paused before the service processes its intent.
            locationRepository.pauseSpoofing()
            orchestrator.handlePause()
            val manualPosition = LatLng(5.0, 5.0)
            locationRepository.setPositionInternal(manualPosition)
            planned.complete(listOf(first, last))

            assertEquals(MockLocationState.PAUSED, locationRepository.mockLocationState.value)
            assertEquals(manualPosition, locationRepository.currentPosition.value)
            assertEquals("pending", locationRepository.activeRouteId.value)
            verify(exactly = 0) { routeReplayEngine.start(any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { walkToEngine.walkToOnce(any(), any(), any(), any()) }

            orchestrator.handleResume(1.4)

            verify(exactly = 1) { routeReplayEngine.start(any(), any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 0) { routeReplayEngine.resume(any(), any(), any()) }
            assertEquals(MockMode.ROUTE_REPLAY, locationRepository.currentMode.value)
            assertEquals(MockLocationState.RUNNING, locationRepository.mockLocationState.value)
        }

    @Test
    fun handleResume_beforePlanningFinishes_doesNotResumeAnEmptyEngine() =
        runTest {
            val planned = CompletableDeferred<List<LatLng>>()
            val first = LatLng(1.0, 1.0)
            val last = LatLng(2.0, 2.0)
            val route = Route("pending", "Pending", waypoints = listOf(Waypoint("a", first, 0), Waypoint("b", last, 1)))
            locationRepository.setPositionInternal(first)
            coEvery { routeRepository.getRouteWithWaypoints("pending") } returns flowOf(route)
            coEvery { osrmClient.resolveRoute(any(), first, last, true, any()) } coAnswers { planned.await() }

            orchestrator.handleStart("pending", 1.4, RouteStartConfig(followRoadsToStart = true, teleportToStart = true))
            orchestrator.handlePause()
            orchestrator.handleResume(1.4)

            verify(exactly = 0) { routeReplayEngine.resume(any(), any(), any()) }
            assertEquals(MockMode.ROUTE_REPLAY, locationRepository.currentMode.value)
            planned.complete(listOf(first, last))
            verify(exactly = 1) { routeReplayEngine.start(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun handleStart_loopingWalkingRoute_bouncesAtEnds() =
        runTest {
            val first = LatLng(1.0, 1.0)
            val last = LatLng(2.0, 2.0)
            val route = Route("loop", "Loop", waypoints = listOf(Waypoint("a", first, 0), Waypoint("b", last, 1)))
            locationRepository.setPositionInternal(first)
            coEvery { routeRepository.getRouteWithWaypoints("loop") } returns flowOf(route)

            orchestrator.handleStart("loop", 1.4, RouteStartConfig(isLooping = true, teleportToStart = true))

            verify {
                routeReplayEngine.start(
                    waypoints = listOf(first, last),
                    speedMs = 1.4,
                    isLooping = true,
                    onPositionUpdate = any(),
                    onComplete = any(),
                    boundaryIndices = null,
                    bounceAtEnds = true,
                )
            }
        }

    @Test
    fun handlePause_duringApproach_keepsManualPositionUntilResume_withoutStartingEmptyReplay() =
        runTest {
            val nextTick = CompletableDeferred<Unit>()
            val firstTick = LatLng(0.1, 0.1)
            val secondTick = LatLng(0.2, 0.2)
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            coEvery { walkToEngine.walkToOnce(any(), any(), any(), any()) } coAnswers {
                val onPosition = arg<suspend (LatLng) -> Unit>(3)
                onPosition(firstTick)
                nextTick.await()
                onPosition(secondTick)
            }

            orchestrator.handleEphemeralStart(listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), 1.4)
            assertEquals(firstTick, locationRepository.currentPosition.value)
            orchestrator.handlePause()
            val manualPosition = LatLng(5.0, 5.0)
            locationRepository.setPositionInternal(manualPosition)
            nextTick.complete(Unit)

            assertEquals(manualPosition, locationRepository.currentPosition.value)
            assertEquals(MockLocationState.PAUSED, locationRepository.mockLocationState.value)
            verify(exactly = 0) { routeReplayEngine.start(any(), any(), any(), any(), any(), any(), any(), any()) }

            orchestrator.handleResume(1.4)

            assertEquals(manualPosition, locationRepository.currentPosition.value)
            verify(exactly = 1) { routeReplayEngine.start(any(), any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 0) { routeReplayEngine.resume(any(), any(), any()) }
        }

    @Test
    fun pausedReplay_rejectsLateTick_preservesRoute_andResumesExistingEngine() =
        runTest {
            val onPosition = slot<(LatLng) -> Unit>()
            val first = LatLng(1.0, 1.0)
            val last = LatLng(2.0, 2.0)
            val route = Route("saved", "Saved", waypoints = listOf(Waypoint("a", first, 0), Waypoint("b", last, 1)))
            coEvery { routeRepository.getRouteWithWaypoints("saved") } returns flowOf(route)
            every {
                routeReplayEngine.start(any(), any(), any(), capture(onPosition), any(), any(), any(), any())
            } returns Unit
            orchestrator.handleStart("saved", 1.4, RouteStartConfig(teleportToStart = true))
            orchestrator.handlePause()
            val manualPosition = LatLng(5.0, 5.0)
            locationRepository.setPositionInternal(manualPosition)

            onPosition.captured.invoke(LatLng(1.5, 1.5))

            assertEquals(manualPosition, locationRepository.currentPosition.value)
            assertEquals("saved", locationRepository.activeRouteId.value)
            assertEquals(listOf(first, last), locationRepository.routeWaypoints.value)
            orchestrator.handleResume(1.4)
            verify(exactly = 1) { routeReplayEngine.pause() }
            verify(exactly = 1) { routeReplayEngine.resume(any(), any(), any()) }
            verify(exactly = 0) { routeReplayEngine.jumpToNextWaypoint(any(), any()) }
        }

    @Test
    fun newStart_afterPausedApproach_dropsOldPauseWithoutRevivingOldApproach() =
        runTest {
            val nextTick = CompletableDeferred<Unit>()
            var firstApproach = true
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            coEvery { walkToEngine.walkToOnce(any(), any(), any(), any()) } coAnswers {
                if (firstApproach) {
                    firstApproach = false
                    nextTick.await()
                    arg<suspend (LatLng) -> Unit>(3).invoke(LatLng(9.0, 9.0))
                }
            }
            orchestrator.handleEphemeralStart(listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), 1.4)
            orchestrator.handlePause()

            orchestrator.handleEphemeralStart(listOf(LatLng(3.0, 3.0), LatLng(4.0, 4.0)), 1.4)
            nextTick.complete(Unit)

            assertEquals(MockLocationState.RUNNING, locationRepository.mockLocationState.value)
            assertNotEquals(LatLng(9.0, 9.0), locationRepository.currentPosition.value)
            verify(exactly = 1) { routeReplayEngine.start(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun newStart_releasesPausedReturnWalk_withoutLettingItFinishTheNewRoute() =
        runTest {
            val nextReturnTick = CompletableDeferred<Unit>()
            var returnFinished = false
            val first = LatLng(1.0, 1.0)
            val last = LatLng(2.0, 2.0)
            val route = Route("returning", "Returning", waypoints = listOf(Waypoint("a", first, 0), Waypoint("b", last, 1)))
            coEvery { routeRepository.getRouteWithWaypoints("returning") } returns flowOf(route)
            coEvery { routeRepository.getRouteWithWaypoints("new") } returns flowOf(route.copy(id = "new"))
            val onComplete = slot<() -> Unit>()
            every {
                routeReplayEngine.start(any(), any(), any(), any(), capture(onComplete), any(), any(), any())
            } returns Unit
            coEvery { walkToEngine.walkToOnce(any(), any(), any(), any()) } coAnswers {
                try {
                    nextReturnTick.await()
                    arg<suspend (LatLng) -> Unit>(3).invoke(LatLng(9.0, 9.0))
                } finally {
                    returnFinished = true
                }
            }
            orchestrator.handleStart("returning", 1.4, RouteStartConfig(teleportToStart = true), LatLng(0.0, 0.0))
            onComplete.captured.invoke()
            orchestrator.handlePause()
            nextReturnTick.complete(Unit)
            assertFalse(returnFinished)

            orchestrator.handleStart("new", 1.4, RouteStartConfig(teleportToStart = true))

            assertTrue("the old return coroutine must leave its paused callback", returnFinished)
            assertEquals("new", locationRepository.activeRouteId.value)
            assertEquals(MockMode.ROUTE_REPLAY, locationRepository.currentMode.value)
            assertEquals(first, locationRepository.currentPosition.value)
        }

    @Test
    fun handleResume_emitsRunningState_and_startsEngine() {
        orchestrator.handleResume(1.4)

        verify { routeReplayEngine.resume(any(), any(), any()) }
        assertTrue(stateChanges.contains(MockLocationState.RUNNING))
    }

    @Test
    fun handleResume_teleportBetweenWaypointsActive_jumpsToNextWaypoint_before_resuming() {
        val next = LatLng(1.0, 2.0)
        every { routeReplayEngine.isTeleportBetweenWaypointsActive() } returns true
        every { routeReplayEngine.jumpToNextWaypoint(any(), any()) } returns next
        every { routeReplayEngine.currentProgress() } returns RouteProgress(2, 4)

        orchestrator.handleResume(1.4)

        verifyOrder {
            routeReplayEngine.jumpToNextWaypoint(any(), any())
            routeReplayEngine.resume(any(), any(), any())
        }
        assertEquals(MockMode.ROUTE_REPLAY, locationRepository.currentMode.value)
        assertEquals(next, locationRepository.currentPosition.value)
        assertEquals(RouteProgress(2, 4), locationRepository.routeProgress.value)
        assertEquals(MockLocationState.RUNNING, locationRepository.mockLocationState.value)
    }

    @Test
    fun handleResume_plainReplay_neverJumpsToNextWaypoint() {
        every { routeReplayEngine.isTeleportBetweenWaypointsActive() } returns false

        orchestrator.handleResume(1.4)

        verify(exactly = 0) { routeReplayEngine.jumpToNextWaypoint(any(), any()) }
        verify { routeReplayEngine.resume(any(), any(), any()) }
        assertEquals(MockLocationState.RUNNING, locationRepository.mockLocationState.value)
    }

    @Test
    fun handleResume_passesTheSteeredPositionToTheEngine() {
        val steered = LatLng(5.0, 5.0)
        locationRepository.setPositionInternal(steered)
        every { routeReplayEngine.isTeleportBetweenWaypointsActive() } returns false

        orchestrator.handleResume(1.4)

        verify { routeReplayEngine.resume(any(), any(), steered) }
    }

    @Test
    fun handleResume_teleportBetweenWaypointsActive_keepsTheHopTargetInsteadOfTheSteeredPosition() {
        locationRepository.setPositionInternal(LatLng(5.0, 5.0))
        every { routeReplayEngine.isTeleportBetweenWaypointsActive() } returns true
        every { routeReplayEngine.jumpToNextWaypoint(any(), any()) } returns LatLng(1.0, 2.0)
        every { routeReplayEngine.currentProgress() } returns RouteProgress(2, 4)

        orchestrator.handleResume(1.4)

        verify { routeReplayEngine.resume(any(), any(), null) }
    }

    @Test
    fun walkToStart_dropsATickComputedBeforeThePause_soResumeDoesNotSnapBack() =
        runTest {
            val nextTick = CompletableDeferred<Unit>()
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            coEvery { walkToEngine.walkToOnce(any(), any(), any(), any()) } coAnswers {
                nextTick.await()
                arg<suspend (LatLng) -> Unit>(3).invoke(LatLng(9.0, 9.0))
            }
            orchestrator.handleEphemeralStart(listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), 1.4)
            orchestrator.handlePause()
            val steered = LatLng(5.0, 5.0)
            locationRepository.setPositionInternal(steered)
            nextTick.complete(Unit)

            orchestrator.handleResume(1.4)

            assertEquals(steered, locationRepository.currentPosition.value)
        }

    @Test
    fun handleStop_clearsRouteProgress() =
        runTest {
            locationRepository.setRouteProgress(RouteProgress(1, 3))
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)

            orchestrator.handleStop()

            assertNull(locationRepository.routeProgress.value)
        }

    @Test
    fun handleStop_whenRunning_callsStartUpdateLoop() =
        runTest {
            locationRepository.startSpoofing()
            assertEquals(MockLocationState.RUNNING, locationRepository.mockLocationState.value)

            orchestrator.handleStop()

            assertTrue(startUpdateLoopCalled)
        }

    @Test
    fun handleStop_whenPaused_doesNotCallStartUpdateLoop() =
        runTest {
            locationRepository.pauseSpoofing()
            assertEquals(MockLocationState.PAUSED, locationRepository.mockLocationState.value)

            orchestrator.handleStop()

            assertFalse(startUpdateLoopCalled)
        }

    @Test
    fun handleStop_clearsModeToTeleport() =
        runTest {
            orchestrator.handleStop()

            assertEquals(MockMode.TELEPORT, locationRepository.currentMode.value)
        }

    @Test
    fun handleStop_stopsEngine() =
        runTest {
            orchestrator.handleStop()

            coVerify { routeReplayEngine.stop() }
        }

    @Test
    fun handleCancel_whenRunning_callsStartUpdateLoop() =
        runTest {
            locationRepository.startSpoofing()

            orchestrator.handleCancel()

            assertTrue(startUpdateLoopCalled)
        }

    @Test
    fun handleCancel_whenIdle_doesNotCallStartUpdateLoop() =
        runTest {
            assertEquals(MockLocationState.IDLE, locationRepository.mockLocationState.value)

            orchestrator.handleCancel()

            assertFalse(startUpdateLoopCalled)
        }

    @Test
    fun handleCancel_clearsModeToTeleport() =
        runTest {
            orchestrator.handleCancel()

            assertEquals(MockMode.TELEPORT, locationRepository.currentMode.value)
        }

    @Test
    fun handleCancel_whenInRouteReplay_transitionsToTeleport() =
        runTest {
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)

            orchestrator.handleCancel()

            assertEquals(MockMode.TELEPORT, locationRepository.currentMode.value)
        }

    @Test
    fun handleCancel_whenInRouteReplay_zeroesSpeed() =
        runTest {
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)

            orchestrator.handleCancel()

            assertEquals(0f, speedChanges.last())
        }

    @Test
    fun handleStop_whenInRouteReplay_zeroesSpeed() =
        runTest {
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)

            orchestrator.handleStop()

            assertEquals(0f, speedChanges.last())
        }

    @Test
    fun handleCancel_doesNotClobberModeSetByNewWalkStartedDuringCancellation() =
        runTest {
            // Simulates the race where cancelAnyActiveMovement() sends an async
            // ACTION_ROUTE_REPLAY_CANCEL intent, then MapController.walkTo() synchronously
            // starts a brand new walk (mode -> WALK_TO) before this cancel actually runs.
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
            locationRepository.setMockMode(MockMode.WALK_TO)

            orchestrator.handleCancel()

            assertEquals(MockMode.WALK_TO, locationRepository.currentMode.value)
        }

    @Test
    fun handleStop_doesNotClobberModeSetByNewWalkStartedDuringCancellation() =
        runTest {
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
            locationRepository.setMockMode(MockMode.WALK_TO)

            orchestrator.handleStop()

            assertEquals(MockMode.WALK_TO, locationRepository.currentMode.value)
        }

    @Test
    fun handleStop_doesNotClobberSpeedWhenNotInRouteReplay() =
        runTest {
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
            locationRepository.setMockMode(MockMode.WALK_TO)

            orchestrator.handleStop()

            assertTrue(speedChanges.isEmpty())
        }

    // Regression coverage for the group-sync leader bug: a route replay that completes naturally
    // *after* being paused and resumed must not force IDLE/stopSpoofing — that unconditionally
    // tore down the leader's test provider and killed broadcasting to followers on completion.
    // The resumed-replay completion path must behave identically to the never-paused one.
    @Test
    fun handleResume_onComplete_doesNotForceIdleState() {
        val onCompleteSlot = slot<() -> Unit>()
        orchestrator.handleResume(1.4)
        verify { routeReplayEngine.resume(any(), capture(onCompleteSlot), any()) }
        stateChanges.clear()

        onCompleteSlot.captured.invoke()

        assertFalse(stateChanges.contains(MockLocationState.IDLE))
    }

    @Test
    fun handleResume_onComplete_doesNotCallStopSpoofing() {
        locationRepository.startSpoofing()
        val onCompleteSlot = slot<() -> Unit>()
        orchestrator.handleResume(1.4)
        verify { routeReplayEngine.resume(any(), capture(onCompleteSlot), any()) }

        onCompleteSlot.captured.invoke()

        assertEquals(MockLocationState.RUNNING, locationRepository.mockLocationState.value)
    }

    @Test
    fun handleResume_onComplete_resetsModeToTeleportAndEmitsCompletion() {
        locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
        val onCompleteSlot = slot<() -> Unit>()
        orchestrator.handleResume(1.4)
        verify { routeReplayEngine.resume(any(), capture(onCompleteSlot), any()) }

        onCompleteSlot.captured.invoke()

        assertEquals(MockMode.TELEPORT, locationRepository.currentMode.value)
    }

    // Regression coverage for GH-48: currentSpeedMs must not stay frozen at the last
    // in-transit value once a replay finishes naturally.
    @Test
    fun handleResume_onComplete_zeroesSpeed() {
        val onCompleteSlot = slot<() -> Unit>()
        orchestrator.handleResume(1.4)
        verify { routeReplayEngine.resume(any(), capture(onCompleteSlot), any()) }
        speedChanges.clear()

        onCompleteSlot.captured.invoke()

        assertEquals(0f, speedChanges.last())
    }

    @Test
    fun handleJumpToNextWaypoint_whenNotInRouteReplay_doesNothing() {
        orchestrator.handleJumpToNextWaypoint()

        verify(exactly = 0) { routeReplayEngine.jumpToNextWaypoint(any(), any()) }
    }

    @Test
    fun handleJumpToNextWaypoint_whenInRouteReplay_callsEngine() {
        locationRepository.setMockMode(MockMode.ROUTE_REPLAY)

        orchestrator.handleJumpToNextWaypoint()

        verify { routeReplayEngine.jumpToNextWaypoint(any(), any()) }
    }

    @Test
    fun handleJumpToPreviousWaypoint_whenInRouteReplay_callsEngine() {
        locationRepository.setMockMode(MockMode.ROUTE_REPLAY)

        orchestrator.handleJumpToPreviousWaypoint()

        verify { routeReplayEngine.jumpToPreviousWaypoint(any(), any()) }
    }

    @Test
    fun handleJumpToNextWaypoint_pushesReturnedPositionImmediately() {
        locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
        val target =
            com.locationjoystick.core.model
                .LatLng(1.0, 2.0)
        every { routeReplayEngine.jumpToNextWaypoint(any(), any()) } returns target

        orchestrator.handleJumpToNextWaypoint()

        assertEquals(target, locationRepository.currentPosition.value)
    }

    @Test
    fun `teleport route hop invokes onTeleport`() =
        runTest {
            coEvery { routeRepository.getRouteWithWaypoints("teleport-1") } returns flowOf(teleportRoute())
            val onPosition = slot<(LatLng) -> Unit>()
            every { teleportRouteEngine.start(any(), any(), any(), capture(onPosition), any()) } returns Unit
            orchestrator.handleStart("teleport-1", 1.4, hopConfig)
            locationRepository.setPositionInternal(LatLng(1.0, 1.0))

            onPosition.captured(LatLng(2.0, 2.0))

            assertEquals(1, teleportCount)
        }

    @Test
    fun `jump to next waypoint invokes onTeleport`() {
        locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
        locationRepository.setPositionInternal(LatLng(1.0, 1.0))
        every { routeReplayEngine.jumpToNextWaypoint(any(), any()) } returns LatLng(2.0, 2.0)

        orchestrator.handleJumpToNextWaypoint()

        assertEquals(1, teleportCount)
    }

    @Test
    fun `a normal walking tick does not invoke onTeleport`() {
        locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
        locationRepository.setPositionInternal(LatLng(1.0, 1.0))
        every { routeReplayEngine.jumpToNextWaypoint(any(), any()) } returns LatLng(1.000001, 1.0)

        orchestrator.handleJumpToNextWaypoint()

        assertEquals(0, teleportCount)
    }

    @Test
    fun handleJumpToNextWaypoint_onReplayComplete_zeroesSpeed() {
        locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
        val onCompleteSlot = slot<() -> Unit>()
        every { routeReplayEngine.jumpToNextWaypoint(any(), capture(onCompleteSlot)) } returns null

        orchestrator.handleJumpToNextWaypoint()
        speedChanges.clear()
        onCompleteSlot.captured.invoke()

        assertEquals(0f, speedChanges.last())
    }

    // walkToPosition (the walk-to-start-of-route phase) is routed through the same
    // tickPosition() helper as the other 3 call sites, so a failed push must not propagate
    // and abort the replay start — matching handleResume/startReplayWithWaypoints' behavior.
    @Test
    fun startReplay_walkToStartOfRoute_swallowsPushLocationUpdateException() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val callbackSlot = slot<suspend (LatLng) -> Unit>()
            coEvery { walkToEngine.walkToOnce(any(), any(), any(), capture(callbackSlot)) } coAnswers {
                callbackSlot.captured.invoke(LatLng(1.0, 1.0))
            }
            val throwingOrchestrator =
                ReplayOrchestrator(
                    context = context,
                    locationRepository = locationRepository,
                    routeRepository = routeRepository,
                    roamingRepository = roamingRepository,
                    routeReplayEngine = routeReplayEngine,
                    teleportRouteEngine = teleportRouteEngine,
                    walkToEngine = walkToEngine,
                    osrmClient = osrmClient,
                    routingErrorReporter = routingErrorReporter,
                    scope = kotlinx.coroutines.CoroutineScope(dispatcher),
                    onStateChange = { },
                    onPositionChange = { _, _ -> },
                    onSpeedChange = { },
                    pushLocationUpdate = { throw RuntimeException("boom") },
                    startUpdateLoop = { },
                )

            throwingOrchestrator.handleEphemeralStart(listOf(LatLng(0.0, 0.0), LatLng(2.0, 2.0)), 1.4)

            assertEquals(LatLng(1.0, 1.0), locationRepository.currentPosition.value)
        }

    @Test
    fun handleStart_followRoadsToStart_walksEachOsrmLegBeforeReplay() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", LatLng(2.0, 2.0), 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", LatLng(3.0, 3.0), 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            coEvery {
                osrmClient.resolveRoute(any(), LatLng(0.0, 0.0), LatLng(2.0, 2.0), true, any())
            } returns listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0), LatLng(2.0, 2.0))
            coEvery {
                osrmClient.resolveRoute(any(), LatLng(2.0, 2.0), LatLng(3.0, 3.0), true, any())
            } returns listOf(LatLng(2.0, 2.0), LatLng(3.0, 3.0))

            coEvery { walkToEngine.walkToOnce(any(), any(), any(), any()) } coAnswers {
                locationRepository.setPositionInternal(secondArg())
            }

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(followRoadsToStart = true, teleportToStart = false))

            coVerify { walkToEngine.walkToOnce(LatLng(0.0, 0.0), LatLng(1.0, 1.0), 1.4, any()) }
            coVerify { walkToEngine.walkToOnce(LatLng(1.0, 1.0), LatLng(2.0, 2.0), 1.4, any()) }
        }

    @Test
    fun handleStart_followRoadsToStart_setsRoadRouteFetchInFlight_duringOsrmResolution() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", LatLng(2.0, 2.0), 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", LatLng(3.0, 3.0), 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            coEvery {
                osrmClient.resolveRoute(any(), LatLng(2.0, 2.0), LatLng(3.0, 3.0), true, any())
            } coAnswers {
                assertTrue(locationRepository.isRoadRouteFetchInFlight.value)
                listOf(LatLng(2.0, 2.0), LatLng(3.0, 3.0))
            }
            coEvery {
                osrmClient.resolveRoute(any(), LatLng(0.0, 0.0), LatLng(2.0, 2.0), true, any())
            } coAnswers {
                assertTrue(locationRepository.isRoadRouteFetchInFlight.value)
                listOf(LatLng(0.0, 0.0), LatLng(2.0, 2.0))
            }

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(followRoadsToStart = true))

            assertFalse(locationRepository.isRoadRouteFetchInFlight.value)
        }

    @Test
    fun handleStart_followRoadsToStartFalse_neverSetsRoadRouteFetchInFlight() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", LatLng(2.0, 2.0), 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", LatLng(3.0, 3.0), 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(followRoadsToStart = false))

            assertFalse(locationRepository.isRoadRouteFetchInFlight.value)
        }

    @Test
    fun handleStart_onComplete_zeroesSpeed() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", LatLng(2.0, 2.0), 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", LatLng(3.0, 3.0), 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            val onCompleteSlot = slot<() -> Unit>()
            every {
                routeReplayEngine.start(any(), any(), any(), any(), capture(onCompleteSlot), any(), any(), any())
            } returns Unit

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig())
            speedChanges.clear()
            onCompleteSlot.captured.invoke()

            assertEquals(0f, speedChanges.last())
        }

    @Test
    fun handleStart_followRoadsToStartFalse_neverCallsOsrmClient() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", LatLng(2.0, 2.0), 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", LatLng(3.0, 3.0), 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig())

            coVerify(exactly = 0) { osrmClient.resolveRoute(any(), any(), any(), any(), any()) }
        }

    @Test
    fun handleStart_followRoadsToStart_expandsBetweenWaypointLegsBeforeReplay() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val w1 = LatLng(2.0, 2.0)
            val w2 = LatLng(3.0, 3.0)
            val w3 = LatLng(4.0, 4.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", w2, 1),
                            com.locationjoystick.core.model
                                .Waypoint("w3", w3, 2),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            coEvery { osrmClient.resolveRoute(any(), LatLng(0.0, 0.0), w1, true, any()) } returns listOf(LatLng(0.0, 0.0), w1)
            val betweenLeg1 = listOf(w1, LatLng(2.5, 2.5), w2)
            val betweenLeg2 = listOf(w2, LatLng(3.5, 3.5), w3)
            coEvery { osrmClient.resolveRoute(any(), w1, w2, true, any()) } returns betweenLeg1
            coEvery { osrmClient.resolveRoute(any(), w2, w3, true, any()) } returns betweenLeg2

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(followRoadsToStart = true))

            val expected = listOf(w1, LatLng(2.5, 2.5), w2, LatLng(3.5, 3.5), w3)
            verify {
                routeReplayEngine.start(
                    waypoints = expected,
                    speedMs = 1.4,
                    isLooping = false,
                    onPositionUpdate = any(),
                    onComplete = any(),
                    boundaryIndices = listOf(0, 2, 4),
                )
            }
        }

    // Pins down that the *expanded* road-following path (not just the engine's
    // waypoints arg, verified above) also feeds LocationRepository.routeWaypoints —
    // the field the map's polyline actually reads (see docs/features/map.md).
    @Test
    fun handleStart_followRoadsToStart_setsRouteWaypointsToExpandedPath() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val w1 = LatLng(2.0, 2.0)
            val w2 = LatLng(3.0, 3.0)
            val w3 = LatLng(4.0, 4.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", w2, 1),
                            com.locationjoystick.core.model
                                .Waypoint("w3", w3, 2),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            coEvery { osrmClient.resolveRoute(any(), LatLng(0.0, 0.0), w1, true, any()) } returns listOf(LatLng(0.0, 0.0), w1)
            val betweenLeg1 = listOf(w1, LatLng(2.5, 2.5), w2)
            val betweenLeg2 = listOf(w2, LatLng(3.5, 3.5), w3)
            coEvery { osrmClient.resolveRoute(any(), w1, w2, true, any()) } returns betweenLeg1
            coEvery { osrmClient.resolveRoute(any(), w2, w3, true, any()) } returns betweenLeg2

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(followRoadsToStart = true))

            val expected = listOf(w1, LatLng(2.5, 2.5), w2, LatLng(3.5, 3.5), w3)
            assertEquals(expected, locationRepository.routeWaypoints.value)
        }

    // Locks in that when the checkbox is off, the map still shows the saved
    // straight-line path (raw, unexpanded) — a future change can't silently
    // start expanding it unconditionally.
    @Test
    fun handleStart_followRoadsToStartFalse_setsRouteWaypointsToRawWaypoints() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val w1 = LatLng(2.0, 2.0)
            val w2 = LatLng(3.0, 3.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", w2, 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(followRoadsToStart = false))

            assertEquals(listOf(w1, w2), locationRepository.routeWaypoints.value)
        }

    @Test
    fun handleStart_followRoadsToStart_reportsFallbackSummaryWhenALegFallsBack() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val w1 = LatLng(2.0, 2.0)
            val w2 = LatLng(3.0, 3.0)
            val w3 = LatLng(4.0, 4.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", w2, 1),
                            com.locationjoystick.core.model
                                .Waypoint("w3", w3, 2),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            coEvery { osrmClient.resolveRoute(any(), LatLng(0.0, 0.0), w1, true, any()) } returns listOf(LatLng(0.0, 0.0), w1)
            coEvery { osrmClient.resolveRoute(any(), w1, w2, true, any()) } returns listOf(w1, w2)
            val onFallbackSlot = slot<(com.locationjoystick.core.routing.OsrmFailureReason) -> Unit>()
            coEvery {
                osrmClient.resolveRoute(any(), w2, w3, true, capture(onFallbackSlot))
            } coAnswers {
                onFallbackSlot.captured.invoke(com.locationjoystick.core.routing.OsrmFailureReason.Timeout)
                listOf(w2, w3)
            }

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(followRoadsToStart = true))

            verify { routingErrorReporter.reportRoadFollowingFallbacks(1, 2) }
        }

    @Test
    fun handleStart_planting_expandsCirclesAroundSavedWaypoints() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val w1 = LatLng(2.0, 2.0)
            val w2 = LatLng(3.0, 3.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", w2, 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            val expected =
                buildPlantingReplayPath(
                    listOf(w1, w2),
                    AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS,
                )

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(isPlanting = true, teleportToStart = true))

            assertEquals(expected.first.first(), locationRepository.currentPosition.value)
            assertEquals(expected.first, locationRepository.routeWaypoints.value)
            coVerify(exactly = 0) { osrmClient.resolveRoute(any(), any(), any(), any(), any()) }
            verify {
                routeReplayEngine.start(
                    waypoints = expected.first,
                    speedMs = 1.4,
                    isLooping = true,
                    onPositionUpdate = any(),
                    onComplete = any(),
                    boundaryIndices = expected.second,
                )
            }
        }

    @Test
    fun handleStart_planting_usesConfiguredRadius() =
        runTest {
            plantingRadius = 100.0
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val center = LatLng(2.0, 2.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", center, 0),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            val expected = buildPlantingReplayPath(listOf(center), 100.0)

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(isPlanting = true, teleportToStart = true))

            verify {
                routeReplayEngine.start(
                    waypoints = expected.first,
                    speedMs = 1.4,
                    isLooping = true,
                    onPositionUpdate = any(),
                    onComplete = any(),
                    boundaryIndices = listOf(0),
                )
            }
        }

    @Test
    fun handleStart_planting_oneWaypoint_startsClosedCircle() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val center = LatLng(2.0, 2.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", center, 0),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            val expected =
                buildPlantingReplayPath(
                    listOf(center),
                    AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS,
                )

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(isPlanting = true, teleportToStart = true))

            assertEquals(expected.first.first(), locationRepository.currentPosition.value)
            assertNotEquals(center, locationRepository.currentPosition.value)
            verify {
                routeReplayEngine.start(
                    waypoints = expected.first,
                    speedMs = 1.4,
                    isLooping = true,
                    onPositionUpdate = any(),
                    onComplete = any(),
                    boundaryIndices = listOf(0),
                )
            }
        }

    @Test
    fun handleStart_plantingReverse_plantsAroundLastWaypointFirst() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val w1 = LatLng(2.0, 2.0)
            val w2 = LatLng(3.0, 3.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", w2, 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            val expected =
                buildPlantingReplayPath(
                    listOf(w2, w1),
                    AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS,
                )

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(isReverse = true, isPlanting = true, teleportToStart = true))

            assertEquals(expected.first.first(), locationRepository.currentPosition.value)
            verify {
                routeReplayEngine.start(
                    waypoints = expected.first,
                    speedMs = 1.4,
                    isLooping = true,
                    onPositionUpdate = any(),
                    onComplete = any(),
                    boundaryIndices = expected.second,
                )
            }
        }

    @Test
    fun handleStart_plantingAndFollowRoads_resolvesConnectorsBetweenRingsNotCenters() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val w1 = LatLng(2.0, 2.0)
            val w2 = LatLng(3.0, 3.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", w2, 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            val radius = AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS
            val rings = plantingRings(listOf(w1, w2), radius)
            val from = rings[0].last()
            val to = rings[1].first()
            val connector = listOf(from, LatLng(2.5, 2.5), to)
            coEvery { osrmClient.resolveRoute(any(), from, to, true, any()) } returns connector
            val expected = stitchRingsWithConnectorsAndBoundaries(rings, listOf(connector))

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(followRoadsToStart = true, isPlanting = true, teleportToStart = true))

            coVerify(exactly = 0) { osrmClient.resolveRoute(any(), w1, w2, true, any()) }
            coVerify(exactly = 1) { osrmClient.resolveRoute(any(), from, to, true, any()) }
            assertEquals(expected.first, locationRepository.routeWaypoints.value)
            verify {
                routeReplayEngine.start(
                    waypoints = expected.first,
                    speedMs = 1.4,
                    isLooping = true,
                    onPositionUpdate = any(),
                    onComplete = any(),
                    boundaryIndices = expected.second,
                )
            }
        }

    @Test
    fun handleStart_teleportBetween_skipsFollowRoadsExpansion() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val w1 = LatLng(2.0, 2.0)
            val w2 = LatLng(3.0, 3.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", w2, 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)

            orchestrator.handleStart(
                "route-1",
                1.4,
                RouteStartConfig(followRoadsToStart = true, teleportBetweenWaypoints = true, teleportToStart = true),
            )

            coVerify(exactly = 0) { osrmClient.resolveRoute(any(), any(), any(), any(), any()) }
            assertEquals(listOf(w1, w2), locationRepository.routeWaypoints.value)
            verify {
                routeReplayEngine.start(
                    waypoints = listOf(w1, w2),
                    speedMs = 1.4,
                    isLooping = false,
                    onPositionUpdate = any(),
                    onComplete = any(),
                    boundaryIndices = null,
                    teleportBetweenWaypoints = true,
                    teleportBetweenDelaySeconds = AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS,
                )
            }
        }

    @Test
    fun handleStart_plantingAndTeleportBetween_doesNotCallOsrmForConnectors() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val w1 = LatLng(2.0, 2.0)
            val w2 = LatLng(3.0, 3.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", w2, 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            val radius = AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS
            val expected = stitchRingsWithoutConnectors(plantingRings(listOf(w1, w2), radius))

            orchestrator.handleStart(
                "route-1",
                1.4,
                RouteStartConfig(followRoadsToStart = true, isPlanting = true, teleportBetweenWaypoints = true, teleportToStart = true),
            )

            coVerify(exactly = 0) { osrmClient.resolveRoute(any(), any(), any(), any(), any()) }
            assertEquals(expected.first, locationRepository.routeWaypoints.value)
            verify {
                routeReplayEngine.start(
                    waypoints = expected.first,
                    speedMs = 1.4,
                    isLooping = true,
                    onPositionUpdate = any(),
                    onComplete = any(),
                    boundaryIndices = expected.second,
                    teleportBetweenWaypoints = true,
                    teleportBetweenDelaySeconds = AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS,
                )
            }
        }

    @Test
    fun handleStart_plantedRoute_usesOwnRadiusWithoutPlantingFlag() =
        runTest {
            plantingRadius = 35.0
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val center = LatLng(2.0, 2.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", center, 0),
                        ),
                    plantingRadiusMeters = 100.0,
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            val expected = buildPlantingReplayPath(listOf(center), 100.0)

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig())

            verify {
                routeReplayEngine.start(
                    waypoints = expected.first,
                    speedMs = 1.4,
                    isLooping = true,
                    onPositionUpdate = any(),
                    onComplete = any(),
                    boundaryIndices = listOf(0),
                )
            }
        }

    @Test
    fun handleStart_plantedTeleportRoute_walksRingsAndSkipsTeleportEngine() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val w1 = LatLng(2.0, 2.0)
            val w2 = LatLng(3.0, 3.0)
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    routeType = com.locationjoystick.core.model.RouteType.TELEPORT,
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", w2, 1),
                        ),
                    plantingRadiusMeters = 60.0,
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            val expected = stitchRingsWithoutConnectors(plantingRings(listOf(w1, w2), 60.0))

            orchestrator.handleStart(
                "route-1",
                1.4,
                RouteStartConfig(isPlanting = true, teleportBetweenWaypoints = true, teleportToStart = true),
            )

            verify(exactly = 0) { teleportRouteEngine.start(any(), any(), any(), any(), any()) }
            verify {
                routeReplayEngine.start(
                    waypoints = expected.first,
                    speedMs = 1.4,
                    isLooping = true,
                    onPositionUpdate = any(),
                    onComplete = any(),
                    boundaryIndices = expected.second,
                    teleportBetweenWaypoints = true,
                    teleportBetweenDelaySeconds = AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS,
                )
            }
        }

    @Test
    fun handleStart_planting_loopsEvenWhenLoopOverrideIsFalse() =
        runTest {
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))
            val route =
                com.locationjoystick.core.model.Route(
                    id = "route-1",
                    name = "R",
                    waypoints =
                        listOf(
                            com.locationjoystick.core.model
                                .Waypoint("w1", LatLng(2.0, 2.0), 0),
                            com.locationjoystick.core.model
                                .Waypoint("w2", LatLng(3.0, 3.0), 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)

            orchestrator.handleStart("route-1", 1.4, RouteStartConfig(isPlanting = true, teleportToStart = true))

            verify {
                routeReplayEngine.start(
                    waypoints = any(),
                    speedMs = 1.4,
                    isLooping = true,
                    onPositionUpdate = any(),
                    onComplete = any(),
                    boundaryIndices = any(),
                )
            }
        }

    @Test
    fun handleEphemeralStart_ticksPublishTravelDirectionBearing() =
        runTest {
            var capturedCallback: ((LatLng) -> Unit)? = null
            every {
                routeReplayEngine.start(any(), any(), any(), any(), any(), any(), any(), any())
            } answers {
                capturedCallback = arg(3)
            }

            orchestrator.handleEphemeralStart(listOf(LatLng(0.0, 0.0), LatLng(2.0, 2.0)), 1.4)

            val p1 = LatLng(0.0, 0.0)
            val p2 = LatLng(1.0, 1.0)
            capturedCallback?.invoke(p1)
            capturedCallback?.invoke(p2)

            val expected = calculateBearing(p1.latitude, p1.longitude, p2.latitude, p2.longitude).toFloat()
            assertEquals(expected, locationRepository.currentBearing.value)
        }

    @Test
    fun handleEphemeralStart_singleTick_doesNotSetBearing() =
        runTest {
            var capturedCallback: ((LatLng) -> Unit)? = null
            every {
                routeReplayEngine.start(any(), any(), any(), any(), any(), any(), any(), any())
            } answers {
                capturedCallback = arg(3)
            }

            orchestrator.handleEphemeralStart(listOf(LatLng(0.0, 0.0), LatLng(2.0, 2.0)), 1.4)

            capturedCallback?.invoke(LatLng(0.0, 0.0))

            assertEquals(null, locationRepository.currentBearing.value)
        }

    @Test
    fun tick_afterModeChangedAwayFromRouteReplay_isIgnored() =
        runTest {
            var pushCount = 0
            var capturedCallback: ((LatLng) -> Unit)? = null
            val orch =
                ReplayOrchestrator(
                    context = context,
                    locationRepository = locationRepository,
                    routeRepository = routeRepository,
                    roamingRepository = roamingRepository,
                    routeReplayEngine = routeReplayEngine,
                    teleportRouteEngine = teleportRouteEngine,
                    walkToEngine = walkToEngine,
                    osrmClient = osrmClient,
                    routingErrorReporter = routingErrorReporter,
                    scope = kotlinx.coroutines.CoroutineScope(dispatcher),
                    onStateChange = { },
                    onPositionChange = { _, _ -> },
                    onSpeedChange = { },
                    pushLocationUpdate = { pushCount++ },
                    startUpdateLoop = { },
                )
            every {
                routeReplayEngine.start(any(), any(), any(), any(), any(), any(), any(), any())
            } answers {
                capturedCallback = arg(3)
            }
            locationRepository.setPositionInternal(LatLng(0.0, 0.0))

            orch.handleEphemeralStart(listOf(LatLng(0.0, 0.0), LatLng(2.0, 2.0)), 1.4)

            // Simulates a new walk-to superseding this replay before its async ACTION_ROUTE_REPLAY_CANCEL
            // teardown (handleCancel, dispatched on serviceScope) actually runs.
            locationRepository.setMockMode(MockMode.WALK_TO)
            locationRepository.setPositionInternal(LatLng(5.0, 5.0))
            pushCount = 0

            capturedCallback?.invoke(LatLng(9.0, 9.0))

            assertEquals(LatLng(5.0, 5.0), locationRepository.currentPosition.value)
            assertEquals(0, pushCount)
        }

    private fun teleportRoute(
        waypoints: List<Waypoint> =
            listOf(
                Waypoint("w1", LatLng(1.0, 1.0), 0, waitSeconds = 3),
                Waypoint("w2", LatLng(2.0, 2.0), 1, waitSeconds = 7),
            ),
    ) = Route(id = "teleport-1", name = "T", waypoints = waypoints, routeType = RouteType.TELEPORT)

    private val hopConfig = RouteStartConfig(teleportToStart = true, teleportBetweenWaypoints = true)

    @Test
    fun handleStart_teleportRouteWithoutHop_walksViaReplayEngine() =
        runTest {
            coEvery { routeRepository.getRouteWithWaypoints("teleport-1") } returns flowOf(teleportRoute())

            orchestrator.handleStart("teleport-1", 1.4, RouteStartConfig())

            verify(exactly = 0) { teleportRouteEngine.start(any(), any(), any(), any(), any()) }
            verify { routeReplayEngine.start(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun handleStart_teleportRoute_callsTeleportEngineNotReplayEngine() =
        runTest {
            coEvery { routeRepository.getRouteWithWaypoints("teleport-1") } returns flowOf(teleportRoute())

            orchestrator.handleStart("teleport-1", 1.4, hopConfig)

            verify {
                teleportRouteEngine.start(
                    waypoints =
                        listOf(
                            Waypoint("w1", LatLng(1.0, 1.0), 0, waitSeconds = 3),
                            Waypoint("w2", LatLng(2.0, 2.0), 1, waitSeconds = 7),
                        ),
                    isLooping = false,
                    onPositionUpdate = any(),
                    onComplete = any(),
                )
            }
            verify(exactly = 0) { routeReplayEngine.start(any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { walkToEngine.walkToOnce(any(), any(), any(), any()) }
        }

    @Test
    fun handleStart_teleportRouteBackward_reversesWaypointsAndWaitSecondsTogether() =
        runTest {
            coEvery { routeRepository.getRouteWithWaypoints("teleport-1") } returns flowOf(teleportRoute())

            orchestrator.handleStart("teleport-1", 1.4, hopConfig.copy(isReverse = true))

            verify {
                teleportRouteEngine.start(
                    waypoints =
                        listOf(
                            Waypoint("w2", LatLng(2.0, 2.0), 1, waitSeconds = 7),
                            Waypoint("w1", LatLng(1.0, 1.0), 0, waitSeconds = 3),
                        ),
                    isLooping = any(),
                    onPositionUpdate = any(),
                    onComplete = any(),
                )
            }
        }

    @Test
    fun handlePause_whileTeleportReplayActive_pausesTeleportEngineNotReplayEngine() =
        runTest {
            coEvery { routeRepository.getRouteWithWaypoints("teleport-1") } returns flowOf(teleportRoute())
            orchestrator.handleStart("teleport-1", 1.4, hopConfig)

            orchestrator.handlePause()

            verify { teleportRouteEngine.pause() }
            verify(exactly = 0) { routeReplayEngine.pause() }
        }

    @Test
    fun handleResume_whileTeleportReplayActive_resumesTeleportEngineNotReplayEngine() =
        runTest {
            coEvery { routeRepository.getRouteWithWaypoints("teleport-1") } returns flowOf(teleportRoute())
            orchestrator.handleStart("teleport-1", 1.4, hopConfig)

            orchestrator.handleResume(1.4)

            verify { teleportRouteEngine.resume(any(), any(), any()) }
            verify(exactly = 0) { routeReplayEngine.resume(any(), any(), any()) }
        }

    @Test
    fun handleStop_whileTeleportReplayActive_stopsTeleportEngineNotReplayEngine() =
        runTest {
            coEvery { routeRepository.getRouteWithWaypoints("teleport-1") } returns flowOf(teleportRoute())
            orchestrator.handleStart("teleport-1", 1.4, hopConfig)

            orchestrator.handleStop()

            // routeReplayEngine.stop() is also called once as unconditional cleanup inside
            // handleStart itself (before the route type is known) — only teleportRouteEngine
            // is the *dispatch target* of handleStop() while a teleport replay is active.
            coVerify(exactly = 2) { teleportRouteEngine.stop() }
            coVerify(exactly = 1) { routeReplayEngine.stop() }
        }

    @Test
    fun handleCancel_whileTeleportReplayActive_stopsTeleportEngineNotReplayEngine() =
        runTest {
            coEvery { routeRepository.getRouteWithWaypoints("teleport-1") } returns flowOf(teleportRoute())
            orchestrator.handleStart("teleport-1", 1.4, hopConfig)

            orchestrator.handleCancel()

            coVerify(exactly = 2) { teleportRouteEngine.stop() }
            coVerify(exactly = 1) { routeReplayEngine.stop() }
        }

    @Test
    fun handleJumpToNextWaypoint_whileTeleportReplayActive_callsTeleportEngine() =
        runTest {
            coEvery { routeRepository.getRouteWithWaypoints("teleport-1") } returns flowOf(teleportRoute())
            orchestrator.handleStart("teleport-1", 1.4, hopConfig)

            orchestrator.handleJumpToNextWaypoint()

            verify { teleportRouteEngine.jumpToNextWaypoint(any(), any()) }
            verify(exactly = 0) { routeReplayEngine.jumpToNextWaypoint(any(), any()) }
        }

    @Test
    fun handleJumpToPreviousWaypoint_whileTeleportReplayActive_callsTeleportEngine() =
        runTest {
            coEvery { routeRepository.getRouteWithWaypoints("teleport-1") } returns flowOf(teleportRoute())
            orchestrator.handleStart("teleport-1", 1.4, hopConfig)

            orchestrator.handleJumpToPreviousWaypoint()

            verify { teleportRouteEngine.jumpToPreviousWaypoint(any(), any()) }
            verify(exactly = 0) { routeReplayEngine.jumpToPreviousWaypoint(any(), any()) }
        }

    @Test
    fun afterTeleportReplayCompletes_nextReplayDispatchesToRouteReplayEngineNotStaleTeleportRef() =
        runTest {
            coEvery { routeRepository.getRouteWithWaypoints("teleport-1") } returns flowOf(teleportRoute())
            val onCompleteSlot = slot<() -> Unit>()
            every {
                teleportRouteEngine.start(any(), any(), any(), any(), capture(onCompleteSlot))
            } returns Unit
            orchestrator.handleStart("teleport-1", 1.4, hopConfig)
            onCompleteSlot.captured.invoke()

            val straightRoute = Route(id = "straight-1", name = "S", waypoints = teleportRoute().waypoints)
            coEvery { routeRepository.getRouteWithWaypoints("straight-1") } returns flowOf(straightRoute)
            orchestrator.handleStart("straight-1", 1.4, RouteStartConfig())

            orchestrator.handlePause()

            verify { routeReplayEngine.pause() }
        }

    @Test
    fun updateSpeed_whileTeleportReplayActive_skipsEngineCallButReportsZero() =
        runTest {
            coEvery { routeRepository.getRouteWithWaypoints("teleport-1") } returns flowOf(teleportRoute())
            orchestrator.handleStart("teleport-1", 1.4, hopConfig)
            speedChanges.clear()

            orchestrator.updateSpeed(5.0)

            verify(exactly = 0) { routeReplayEngine.updateSpeed(any()) }
            assertEquals(0f, speedChanges.last())
        }
}
