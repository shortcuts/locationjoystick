package com.locationjoystick.core.location

import com.locationjoystick.core.common.util.calculateBearing
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.WalkToEngine
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RouteReplayEngine
import com.locationjoystick.core.routing.RoutingErrorReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayOrchestratorTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val locationRepository = LocationRepository()
    private val routeRepository: RouteRepository = mockk(relaxed = true)
    private val roamingRepository: RoamingRepository = mockk(relaxed = true)
    private val routeReplayEngine: RouteReplayEngine = mockk(relaxed = true)
    private val walkToEngine: WalkToEngine = mockk(relaxed = true)
    private val osrmClient: OsrmClient = mockk(relaxed = true)
    private val routingErrorReporter: RoutingErrorReporter = mockk(relaxed = true)

    private val stateChanges = mutableListOf<MockLocationState>()
    private val speedChanges = mutableListOf<Float>()
    private var startUpdateLoopCalled = false

    private lateinit var orchestrator: ReplayOrchestrator

    @Before
    fun setup() {
        stateChanges.clear()
        speedChanges.clear()
        startUpdateLoopCalled = false
        orchestrator =
            ReplayOrchestrator(
                locationRepository = locationRepository,
                routeRepository = routeRepository,
                roamingRepository = roamingRepository,
                routeReplayEngine = routeReplayEngine,
                walkToEngine = walkToEngine,
                osrmClient = osrmClient,
                routingErrorReporter = routingErrorReporter,
                scope = kotlinx.coroutines.CoroutineScope(dispatcher),
                onStateChange = { stateChanges.add(it) },
                onPositionChange = { _, _ -> },
                onSpeedChange = { speedChanges.add(it) },
                pushLocationUpdate = { },
                startUpdateLoop = { startUpdateLoopCalled = true },
            )
    }

    @Test
    fun handlePause_pausesEngine_and_emitsState() {
        orchestrator.handlePause()

        verify { routeReplayEngine.pause() }
        assertEquals(MockLocationState.PAUSED, stateChanges.last())
        assertEquals(MockLocationState.PAUSED, locationRepository.mockLocationState.value)
    }

    @Test
    fun handleResume_emitsRunningState_and_startsEngine() {
        orchestrator.handleResume(1.4)

        verify { routeReplayEngine.resume(any(), any()) }
        assertTrue(stateChanges.contains(MockLocationState.RUNNING))
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
        verify { routeReplayEngine.resume(any(), capture(onCompleteSlot)) }
        stateChanges.clear()

        onCompleteSlot.captured.invoke()

        assertFalse(stateChanges.contains(MockLocationState.IDLE))
    }

    @Test
    fun handleResume_onComplete_doesNotCallStopSpoofing() {
        locationRepository.startSpoofing()
        val onCompleteSlot = slot<() -> Unit>()
        orchestrator.handleResume(1.4)
        verify { routeReplayEngine.resume(any(), capture(onCompleteSlot)) }

        onCompleteSlot.captured.invoke()

        assertEquals(MockLocationState.RUNNING, locationRepository.mockLocationState.value)
    }

    @Test
    fun handleResume_onComplete_resetsModeToTeleportAndEmitsCompletion() {
        locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
        val onCompleteSlot = slot<() -> Unit>()
        orchestrator.handleResume(1.4)
        verify { routeReplayEngine.resume(any(), capture(onCompleteSlot)) }

        onCompleteSlot.captured.invoke()

        assertEquals(MockMode.TELEPORT, locationRepository.currentMode.value)
    }

    // Regression coverage for GH-48: currentSpeedMs must not stay frozen at the last
    // in-transit value once a replay finishes naturally.
    @Test
    fun handleResume_onComplete_zeroesSpeed() {
        val onCompleteSlot = slot<() -> Unit>()
        orchestrator.handleResume(1.4)
        verify { routeReplayEngine.resume(any(), capture(onCompleteSlot)) }
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
        val target = com.locationjoystick.core.model.LatLng(1.0, 2.0)
        every { routeReplayEngine.jumpToNextWaypoint(any(), any()) } returns target

        orchestrator.handleJumpToNextWaypoint()

        assertEquals(target, locationRepository.currentPosition.value)
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
                    locationRepository = locationRepository,
                    routeRepository = routeRepository,
                    roamingRepository = roamingRepository,
                    routeReplayEngine = routeReplayEngine,
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
                            com.locationjoystick.core.model.Waypoint("w1", LatLng(2.0, 2.0), 0),
                            com.locationjoystick.core.model.Waypoint("w2", LatLng(3.0, 3.0), 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            coEvery {
                osrmClient.resolveRoute(any(), LatLng(0.0, 0.0), LatLng(2.0, 2.0), true, any())
            } returns listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0), LatLng(2.0, 2.0))
            coEvery {
                osrmClient.resolveRoute(any(), LatLng(2.0, 2.0), LatLng(3.0, 3.0), true, any())
            } returns listOf(LatLng(2.0, 2.0), LatLng(3.0, 3.0))

            orchestrator.handleStart("route-1", isBackward = false, speedMs = 1.4, followRoadsToStart = true)

            coVerify { walkToEngine.walkToOnce(LatLng(0.0, 0.0), LatLng(1.0, 1.0), 1.4, any()) }
            coVerify { walkToEngine.walkToOnce(LatLng(1.0, 1.0), LatLng(2.0, 2.0), 1.4, any()) }
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
                            com.locationjoystick.core.model.Waypoint("w1", LatLng(2.0, 2.0), 0),
                            com.locationjoystick.core.model.Waypoint("w2", LatLng(3.0, 3.0), 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            val onCompleteSlot = slot<() -> Unit>()
            every {
                routeReplayEngine.start(any(), any(), any(), any(), capture(onCompleteSlot), any())
            } returns Unit

            orchestrator.handleStart("route-1", isBackward = false, speedMs = 1.4)
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
                            com.locationjoystick.core.model.Waypoint("w1", LatLng(2.0, 2.0), 0),
                            com.locationjoystick.core.model.Waypoint("w2", LatLng(3.0, 3.0), 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)

            orchestrator.handleStart("route-1", isBackward = false, speedMs = 1.4)

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
                            com.locationjoystick.core.model.Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model.Waypoint("w2", w2, 1),
                            com.locationjoystick.core.model.Waypoint("w3", w3, 2),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            coEvery { osrmClient.resolveRoute(any(), LatLng(0.0, 0.0), w1, true, any()) } returns listOf(LatLng(0.0, 0.0), w1)
            val betweenLeg1 = listOf(w1, LatLng(2.5, 2.5), w2)
            val betweenLeg2 = listOf(w2, LatLng(3.5, 3.5), w3)
            coEvery { osrmClient.resolveRoute(any(), w1, w2, true, any()) } returns betweenLeg1
            coEvery { osrmClient.resolveRoute(any(), w2, w3, true, any()) } returns betweenLeg2

            orchestrator.handleStart("route-1", isBackward = false, speedMs = 1.4, followRoadsToStart = true)

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
                            com.locationjoystick.core.model.Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model.Waypoint("w2", w2, 1),
                            com.locationjoystick.core.model.Waypoint("w3", w3, 2),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)
            coEvery { osrmClient.resolveRoute(any(), LatLng(0.0, 0.0), w1, true, any()) } returns listOf(LatLng(0.0, 0.0), w1)
            val betweenLeg1 = listOf(w1, LatLng(2.5, 2.5), w2)
            val betweenLeg2 = listOf(w2, LatLng(3.5, 3.5), w3)
            coEvery { osrmClient.resolveRoute(any(), w1, w2, true, any()) } returns betweenLeg1
            coEvery { osrmClient.resolveRoute(any(), w2, w3, true, any()) } returns betweenLeg2

            orchestrator.handleStart("route-1", isBackward = false, speedMs = 1.4, followRoadsToStart = true)

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
                            com.locationjoystick.core.model.Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model.Waypoint("w2", w2, 1),
                        ),
                )
            coEvery { routeRepository.getRouteWithWaypoints("route-1") } returns kotlinx.coroutines.flow.flowOf(route)

            orchestrator.handleStart("route-1", isBackward = false, speedMs = 1.4, followRoadsToStart = false)

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
                            com.locationjoystick.core.model.Waypoint("w1", w1, 0),
                            com.locationjoystick.core.model.Waypoint("w2", w2, 1),
                            com.locationjoystick.core.model.Waypoint("w3", w3, 2),
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

            orchestrator.handleStart("route-1", isBackward = false, speedMs = 1.4, followRoadsToStart = true)

            verify { routingErrorReporter.reportRoadFollowingFallbacks(1, 2) }
        }

    @Test
    fun handleEphemeralStart_ticksPublishTravelDirectionBearing() =
        runTest {
            var capturedCallback: ((LatLng) -> Unit)? = null
            every {
                routeReplayEngine.start(any(), any(), any(), any(), any(), any())
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
                routeReplayEngine.start(any(), any(), any(), any(), any(), any())
            } answers {
                capturedCallback = arg(3)
            }

            orchestrator.handleEphemeralStart(listOf(LatLng(0.0, 0.0), LatLng(2.0, 2.0)), 1.4)

            capturedCallback?.invoke(LatLng(0.0, 0.0))

            assertEquals(null, locationRepository.currentBearing.value)
        }
}
