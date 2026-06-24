package com.locationjoystick.feature.map.impl

import android.content.Context
import com.locationjoystick.core.data.DeepLinkRepository
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.location.EphemeralReplayController
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.location.StartRouteReplayUseCase
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoutingErrorReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var locationRepository: LocationRepository
    private lateinit var routeRepository: RouteRepository
    private lateinit var favoriteRepository: FavoriteRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var roamingRepository: RoamingRepository
    private lateinit var startRouteReplayUseCase: StartRouteReplayUseCase
    private lateinit var walkCoordinator: WalkCoordinator
    private lateinit var teleportUseCase: TeleportUseCase
    private lateinit var ephemeralReplayController: EphemeralReplayController
    private lateinit var osrmClient: OsrmClient
    private lateinit var deepLinkRepository: DeepLinkRepository
    private lateinit var mapController: MapController
    private lateinit var viewModel: MapViewModel

    private val walkTargetFlow = MutableStateFlow<LatLng?>(null)
    private val isWalkPausedFlow = MutableStateFlow(false)
    private val pendingWaypointsFlow = MutableStateFlow<List<LatLng>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        routeRepository = mockk()
        favoriteRepository = mockk()
        settingsRepository = mockk(relaxed = true)
        roamingRepository = mockk(relaxed = true)
        startRouteReplayUseCase = mockk(relaxed = true)
        walkCoordinator = mockk(relaxed = true)
        teleportUseCase = mockk(relaxed = true)
        ephemeralReplayController = mockk(relaxed = true)
        osrmClient = mockk(relaxed = true)
        deepLinkRepository = mockk(relaxed = true)

        every { locationRepository.currentPosition } returns MutableStateFlow(null)
        every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.IDLE)
        every { locationRepository.isWalkPaused } returns isWalkPausedFlow
        every { locationRepository.walkTarget } returns walkTargetFlow
        every { locationRepository.currentMode } returns MutableStateFlow(MockMode.JOYSTICK)
        every { locationRepository.routeWaypoints } returns MutableStateFlow(null)
        every { ephemeralReplayController.pendingWaypoints } returns pendingWaypointsFlow
        every { routeRepository.getRoutes() } returns flowOf(emptyList<Route>())
        every { favoriteRepository.getFavorites() } returns flowOf(emptyList<FavoriteLocation>())
        every { roamingRepository.isRoaming } returns MutableStateFlow(false)
        every { roamingRepository.isRoamingPaused } returns MutableStateFlow(false)
        every { settingsRepository.getActiveSpeedProfile() } returns
            flowOf(SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = 1.39))
        every { settingsRepository.getRememberLastLocation() } returns flowOf(false)
        every { settingsRepository.getLastLocation() } returns flowOf(null)
        every { settingsRepository.getLastTeleportTime() } returns flowOf(0L)

        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMapController(): MapController =
        MapController(
            context = context,
            locationRepository = locationRepository,
            routeRepository = routeRepository,
            favoriteRepository = favoriteRepository,
            settingsRepository = settingsRepository,
            roamingRepository = roamingRepository,
            walkCoordinator = walkCoordinator,
            teleportUseCase = teleportUseCase,
            startRouteReplayUseCase = startRouteReplayUseCase,
            ephemeralReplayController = ephemeralReplayController,
            osrmClient = osrmClient,
            routingErrorReporter = RoutingErrorReporter(),
            appScope = CoroutineScope(testDispatcher),
        )

    private fun createViewModel(): MapViewModel {
        mapController = createMapController()
        return MapViewModel(
            mapController = mapController,
            roamingRepository = roamingRepository,
            deepLinkRepository = deepLinkRepository,
            teleportUseCase = teleportUseCase,
            settingsRepository = settingsRepository,
        )
    }

    @Test
    fun `tapToTeleport_whenNotSpoofing_teleportsDirectly`() =
        runTest {
            // isSpoofing == false (default IDLE state)
            val position = LatLng(48.8566, 2.3522)

            viewModel.onAction(MapAction.TapToTeleport(position))

            assertNull(viewModel.uiState.value.pendingTapPosition)
        }

    @Test
    fun `tapToTeleport_whenSpoofing_setsPendingPosition`() =
        runTest {
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val position = LatLng(48.8566, 2.3522)
            viewModel.onAction(MapAction.TapToTeleport(position))

            assertEquals(position, viewModel.uiState.value.pendingTapPosition)
        }

    @Test
    fun `confirmTeleport_teleportsAndClearsPending`() =
        runTest {
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val position = LatLng(48.8566, 2.3522)
            viewModel.onAction(MapAction.TapToTeleport(position))
            assertEquals(position, viewModel.uiState.value.pendingTapPosition)

            viewModel.onAction(MapAction.ConfirmTeleport(position))

            assertNull(viewModel.uiState.value.pendingTapPosition)
        }

    @Test
    fun `clearPendingTap_closesSheetButKeepsPin`() =
        runTest {
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.TapToTeleport(LatLng(1.0, 2.0)))
            assertEquals(LatLng(1.0, 2.0), viewModel.uiState.value.pendingTapPosition)
            assertEquals(true, viewModel.uiState.value.isPendingTapSheetOpen)

            viewModel.onAction(MapAction.ClearPendingTap)

            assertEquals(LatLng(1.0, 2.0), viewModel.uiState.value.pendingTapPosition)
            assertEquals(false, viewModel.uiState.value.isPendingTapSheetOpen)
        }

    @Test
    fun `tapToTeleport_preservesExactCoordinates`() =
        runTest {
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val position = LatLng(51.5074, -0.1278)
            viewModel.onAction(MapAction.TapToTeleport(position))

            val pending = viewModel.uiState.value.pendingTapPosition
            org.junit.Assert.assertNotNull(pending)
            assertEquals(position.latitude, pending!!.latitude, 0.00001)
            assertEquals(position.longitude, pending.longitude, 0.00001)
        }

    @Test
    fun `confirmTeleport_preservesExactCoordinates`() =
        runTest {
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val position = LatLng(48.8566, 2.3522)
            viewModel.onAction(MapAction.TapToTeleport(position))
            viewModel.onAction(MapAction.ConfirmTeleport(position))

            // Position should be cleared after confirm
            assertNull(viewModel.uiState.value.pendingTapPosition)
        }

    @Test
    fun `multiple taps update pending position sequentially`() =
        runTest {
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val pos1 = LatLng(0.0, 0.0)
            val pos2 = LatLng(1.0, 1.0)
            val pos3 = LatLng(2.0, 2.0)

            viewModel.onAction(MapAction.TapToTeleport(pos1))
            assertEquals(pos1, viewModel.uiState.value.pendingTapPosition)

            viewModel.onAction(MapAction.TapToTeleport(pos2))
            assertEquals(pos2, viewModel.uiState.value.pendingTapPosition)

            viewModel.onAction(MapAction.TapToTeleport(pos3))
            assertEquals(pos3, viewModel.uiState.value.pendingTapPosition)
        }

    @Test
    fun `clearPendingTap_closesSheetAndKeepsPin`() =
        runTest {
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val position = LatLng(10.5, 20.5)
            viewModel.onAction(MapAction.TapToTeleport(position))
            assertEquals(position, viewModel.uiState.value.pendingTapPosition)
            assertEquals(true, viewModel.uiState.value.isPendingTapSheetOpen)

            viewModel.onAction(MapAction.ClearPendingTap)
            assertEquals(position, viewModel.uiState.value.pendingTapPosition)
            assertEquals(false, viewModel.uiState.value.isPendingTapSheetOpen)
        }

    // Walk lifecycle tests

    @Test
    fun `pauseWalk_setsWalkPausedTrue`() =
        runTest {
            viewModel.onAction(MapAction.PauseWalk)
            verify { locationRepository.setWalkPaused(true) }
        }

    @Test
    fun `resumeWalk_setsWalkPausedFalse`() =
        runTest {
            viewModel.onAction(MapAction.ResumeWalk)
            verify { locationRepository.setWalkPaused(false) }
        }

    @Test
    fun `stopWalk_callsSetWalkTargetNullAndClearsUiState`() =
        runTest {
            val target = LatLng(1.0, 2.0)
            viewModel.onAction(MapAction.LongPressTapToWalk(target))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.StopWalk)
            testDispatcher.scheduler.advanceUntilIdle()

            verify { walkCoordinator.cancel() }
            assertNull(viewModel.uiState.value.walkTarget)
            assertNull(viewModel.uiState.value.walkStart)
        }

    @Test
    fun `walkLoop_breaksWhenWalkTargetClearedExternally`() =
        runTest {
            val target = LatLng(48.0, 2.0)

            viewModel.onAction(MapAction.LongPressTapToWalk(target))
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(target, viewModel.uiState.value.walkTarget)

            // StopWalk (e.g. triggered by widget) cancels coordinator and clears UI state
            viewModel.onAction(MapAction.StopWalk)
            testDispatcher.scheduler.advanceUntilIdle()

            verify { walkCoordinator.cancel() }
            assertNull(viewModel.uiState.value.walkTarget)
            assertNull(viewModel.uiState.value.walkStart)
        }

    @Test
    fun `walkLoop_remainsActiveWhilePausedExternally`() =
        runTest {
            val target = LatLng(48.0, 2.0)

            viewModel.onAction(MapAction.LongPressTapToWalk(target))
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(target, viewModel.uiState.value.walkTarget)

            // Pause externally (widget)
            isWalkPausedFlow.value = true
            testDispatcher.scheduler.advanceTimeBy(100)

            // Walk must still be active — not terminated
            assertEquals(target, viewModel.uiState.value.walkTarget)

            // Cancel the walk job to prevent infinite loop in test
            viewModel.onAction(MapAction.StopWalk)
        }

    // Ephemeral waypoint tests

    @Test
    fun `addEphemeralWaypoint_firstPoint_cancelsCoordinatorAndBuildsThreePointList`() =
        runTest {
            val currentPos = LatLng(48.8, 2.3)
            val walkTarget = LatLng(48.9, 2.4)
            val newPoint = LatLng(49.0, 2.5)
            val resultWaypoints = listOf(currentPos, walkTarget, newPoint)

            every { locationRepository.currentPosition } returns MutableStateFlow(currentPos)
            coEvery { ephemeralReplayController.addWaypoint(any(), any(), any(), any(), any(), any(), any()) } answers {
                pendingWaypointsFlow.value = resultWaypoints
                resultWaypoints
            }
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.LongPressTapToWalk(walkTarget))
            viewModel.onAction(MapAction.AddEphemeralWaypoint(newPoint))
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.walkTarget)
            assertEquals(3, state.ephemeralWaypoints.size)
            assertEquals(currentPos, state.ephemeralWaypoints[0])
            assertEquals(walkTarget, state.ephemeralWaypoints[1])
            assertEquals(newPoint, state.ephemeralWaypoints[2])
        }

    @Test
    fun `addEphemeralWaypoint_secondPoint_appendsToExistingList`() =
        runTest {
            val currentPos = LatLng(48.8, 2.3)
            val walkTarget = LatLng(48.9, 2.4)
            val firstExtra = LatLng(49.0, 2.5)
            val secondExtra = LatLng(49.1, 2.6)
            val list3 = listOf(currentPos, walkTarget, firstExtra)
            val list4 = listOf(currentPos, walkTarget, firstExtra, secondExtra)

            every { locationRepository.currentPosition } returns MutableStateFlow(currentPos)
            var callCount = 0
            coEvery { ephemeralReplayController.addWaypoint(any(), any(), any(), any(), any(), any(), any()) } answers {
                callCount++
                val result = if (callCount == 1) list3 else list4
                pendingWaypointsFlow.value = result
                result
            }
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.LongPressTapToWalk(walkTarget))
            viewModel.onAction(MapAction.AddEphemeralWaypoint(firstExtra))
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(3, viewModel.uiState.value.ephemeralWaypoints.size)

            viewModel.onAction(MapAction.AddEphemeralWaypoint(secondExtra))
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(4, state.ephemeralWaypoints.size)
            assertEquals(secondExtra, state.ephemeralWaypoints.last())
        }

    // Issue 8: restore last location on init

    @Test
    fun `init_restoresLastLocation_whenRememberEnabledAndNoCurrentPosition`() =
        runTest {
            val lastPos = LatLng(48.8566, 2.3522)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            every { settingsRepository.getRememberLastLocation() } returns flowOf(true)
            every { settingsRepository.getLastLocation() } returns flowOf(lastPos)

            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            verify { locationRepository.setPositionInternal(lastPos) }
        }

    @Test
    fun `init_doesNotRestoreLastLocation_whenCurrentPositionAlreadySet`() =
        runTest {
            val currentPos = LatLng(51.5074, -0.1278)
            every { locationRepository.currentPosition } returns MutableStateFlow(currentPos)
            every { settingsRepository.getRememberLastLocation() } returns flowOf(true)
            every { settingsRepository.getLastLocation() } returns flowOf(LatLng(0.0, 0.0))

            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            verify(exactly = 0) { locationRepository.setPositionInternal(any()) }
        }

    @Test
    fun `init_doesNotRestoreLastLocation_whenRememberDisabled`() =
        runTest {
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            every { settingsRepository.getRememberLastLocation() } returns flowOf(false)
            every { settingsRepository.getLastLocation() } returns flowOf(LatLng(48.8566, 2.3522))

            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            verify(exactly = 0) { locationRepository.setPositionInternal(any()) }
        }

    @Test
    fun `stopWalk_clearsEphemeralWaypoints`() =
        runTest {
            val currentPos = LatLng(48.8, 2.3)
            val walkTarget = LatLng(48.9, 2.4)
            val extra = LatLng(49.0, 2.5)
            val resultWaypoints = listOf(currentPos, walkTarget, extra)

            every { locationRepository.currentPosition } returns MutableStateFlow(currentPos)
            coEvery { ephemeralReplayController.addWaypoint(any(), any(), any(), any(), any(), any(), any()) } answers {
                pendingWaypointsFlow.value = resultWaypoints
                resultWaypoints
            }
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.LongPressTapToWalk(walkTarget))
            viewModel.onAction(MapAction.AddEphemeralWaypoint(extra))
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(3, viewModel.uiState.value.ephemeralWaypoints.size)

            viewModel.onAction(MapAction.StopWalk)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(emptyList<LatLng>(), state.ephemeralWaypoints)
            assertNull(state.walkTarget)
        }

    // Walk controls expansion tests

    @Test
    fun `toggleWalkControls_expandsAndCollapsesControls`() =
        runTest {
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.ToggleWalkControls)
            assertEquals(true, viewModel.uiState.value.isWalkControlsExpanded)

            viewModel.onAction(MapAction.ToggleWalkControls)
            assertEquals(false, viewModel.uiState.value.isWalkControlsExpanded)
        }

    @Test
    fun `stopWalk_resetsWalkControlsExpanded`() =
        runTest {
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.ToggleWalkControls)
            assertEquals(true, viewModel.uiState.value.isWalkControlsExpanded)

            viewModel.onAction(MapAction.StopWalk)
            assertEquals(false, viewModel.uiState.value.isWalkControlsExpanded)
        }

    @Test
    fun `ephemeralReplay_ephemeralWaypointsNotEmpty_whenInEphemeralReplayMode`() =
        runTest {
            val currentPos = LatLng(48.8, 2.3)
            val walkTarget = LatLng(48.9, 2.4)
            val extra = LatLng(49.0, 2.5)
            val waypoints = listOf(currentPos, walkTarget, extra)

            every { locationRepository.currentPosition } returns MutableStateFlow(currentPos)
            coEvery { ephemeralReplayController.addWaypoint(any(), any(), any(), any(), any(), any(), any()) } answers {
                pendingWaypointsFlow.value = waypoints
                waypoints
            }
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.LongPressTapToWalk(walkTarget))
            viewModel.onAction(MapAction.AddEphemeralWaypoint(extra))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                true,
                viewModel.uiState.value.ephemeralWaypoints
                    .isNotEmpty(),
            )
        }

    // WalkViaRoadsTo tests

    @Test
    fun `walkViaRoadsTo_onOsrmSuccess_callsStartWalkAlongRouteNotStartWalk`() =
        runTest {
            val currentPos = LatLng(48.8566, 2.3522)
            val target = LatLng(48.8600, 2.3600)
            val osrmWaypoints = listOf(currentPos, LatLng(48.858, 2.354), LatLng(48.860, 2.358), target)

            every { locationRepository.currentPosition } returns MutableStateFlow(currentPos)
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            coEvery { osrmClient.getRoute(any(), any()) } returns Result.success(osrmWaypoints)

            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.WalkViaRoadsTo(target))
            testDispatcher.scheduler.advanceUntilIdle()

            verify { walkCoordinator.startWalkAlongRoute(osrmWaypoints, any(), any()) }
            verify(exactly = 0) { walkCoordinator.startWalk(any(), any(), any()) }
        }

    @Test
    fun `walkViaRoadsTo_onOsrmFailure_fallsBackToStraightWalk`() =
        runTest {
            val currentPos = LatLng(48.8566, 2.3522)
            val target = LatLng(48.8600, 2.3600)

            every { locationRepository.currentPosition } returns MutableStateFlow(currentPos)
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            coEvery { osrmClient.getRoute(any(), any()) } returns Result.failure(RuntimeException("network error"))

            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.WalkViaRoadsTo(target))
            testDispatcher.scheduler.advanceUntilIdle()

            verify { walkCoordinator.startWalk(target, any(), any()) }
            verify(exactly = 0) { walkCoordinator.startWalkAlongRoute(any(), any(), any()) }
        }

    // Sheet visibility tests

    @Test
    fun `OpenFavoritesPicker sets showFavoritesSheet true`() =
        runTest {
            viewModel.onAction(MapAction.OpenFavoritesPicker)
            assertEquals(true, viewModel.uiState.value.showFavoritesSheet)
        }

    @Test
    fun `CloseFavoritesPicker sets showFavoritesSheet false`() =
        runTest {
            viewModel.onAction(MapAction.OpenFavoritesPicker)
            viewModel.onAction(MapAction.CloseFavoritesPicker)
            assertEquals(false, viewModel.uiState.value.showFavoritesSheet)
        }

    @Test
    fun `OpenRoutesSheet sets showRoutesSheet true`() =
        runTest {
            viewModel.onAction(MapAction.OpenRoutesSheet)
            assertEquals(true, viewModel.uiState.value.showRoutesSheet)
        }

    @Test
    fun `CloseRoutesSheet sets showRoutesSheet false`() =
        runTest {
            viewModel.onAction(MapAction.OpenRoutesSheet)
            viewModel.onAction(MapAction.CloseRoutesSheet)
            assertEquals(false, viewModel.uiState.value.showRoutesSheet)
        }

    @Test
    fun `OpenRoamingSheet sets showRoamingSheet true and populates draft`() =
        runTest {
            viewModel.onAction(MapAction.OpenRoamingSheet)
            assertEquals(true, viewModel.uiState.value.showRoamingSheet)
            assertNotNull(viewModel.uiState.value.roamingDraft)
        }

    @Test
    fun `DismissRoamingSheet clears showRoamingSheet and draft`() =
        runTest {
            viewModel.onAction(MapAction.OpenRoamingSheet)
            viewModel.onAction(MapAction.DismissRoamingSheet)
            assertEquals(false, viewModel.uiState.value.showRoamingSheet)
            assertNull(viewModel.uiState.value.roamingDraft)
        }

    // Camera tests

    @Test
    fun `UserStartedPanning sets isUserPanning true`() =
        runTest {
            viewModel.onAction(MapAction.UserStartedPanning)
            assertEquals(true, viewModel.uiState.value.isUserPanning)
        }

    @Test
    fun `RecenterCamera clears isUserPanning`() =
        runTest {
            viewModel.onAction(MapAction.UserStartedPanning)
            viewModel.onAction(MapAction.RecenterCamera)
            assertEquals(false, viewModel.uiState.value.isUserPanning)
        }

    @Test
    fun `CameraTargetConsumed clears pendingCameraTarget`() =
        runTest {
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.TapToTeleport(LatLng(1.0, 2.0)))
            // pendingCameraTarget is set alongside pendingTapPosition for deep links;
            // after consuming, it should be null
            viewModel.onAction(MapAction.CameraTargetConsumed)
            assertNull(viewModel.uiState.value.pendingCameraTarget)
        }

    // Route controls tests

    @Test
    fun `ToggleRouteControls expands and collapses`() =
        runTest {
            viewModel.onAction(MapAction.ToggleRouteControls)
            assertEquals(true, viewModel.uiState.value.isRouteControlsExpanded)

            viewModel.onAction(MapAction.ToggleRouteControls)
            assertEquals(false, viewModel.uiState.value.isRouteControlsExpanded)
        }

    @Test
    fun `StopRouteReplay clears isRouteControlsExpanded`() =
        runTest {
            viewModel.onAction(MapAction.ToggleRouteControls)
            assertEquals(true, viewModel.uiState.value.isRouteControlsExpanded)

            viewModel.onAction(MapAction.StopRouteReplay)
            assertEquals(false, viewModel.uiState.value.isRouteControlsExpanded)
        }

    @Test
    fun `StartRouteReplay calls startRouteReplayUseCase and closes routes sheet`() =
        runTest {
            viewModel.onAction(MapAction.OpenRoutesSheet)
            assertEquals(true, viewModel.uiState.value.showRoutesSheet)

            viewModel.onAction(
                MapAction.StartRouteReplay(
                    routeId = "route-1",
                    isLooping = false,
                    isReverse = false,
                    isReturnToLocation = false,
                    teleportToStart = false,
                ),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { startRouteReplayUseCase.execute("route-1", false, false, false, false) }
            assertEquals(false, viewModel.uiState.value.showRoutesSheet)
        }

    @Test
    fun `PauseRouteReplay sends intent`() =
        runTest {
            viewModel.onAction(MapAction.PauseRouteReplay)
            verify { context.startService(any()) }
        }

    @Test
    fun `StopRouteReplay sends intent`() =
        runTest {
            viewModel.onAction(MapAction.StopRouteReplay)
            verify { context.startService(any()) }
        }

    // Roaming controls tests

    @Test
    fun `ToggleRoamingControls expands and collapses`() =
        runTest {
            viewModel.onAction(MapAction.ToggleRoamingControls)
            assertEquals(true, viewModel.uiState.value.isRoamingControlsExpanded)

            viewModel.onAction(MapAction.ToggleRoamingControls)
            assertEquals(false, viewModel.uiState.value.isRoamingControlsExpanded)
        }

    @Test
    fun `StopRoaming clears isRoamingControlsExpanded`() =
        runTest {
            viewModel.onAction(MapAction.ToggleRoamingControls)
            assertEquals(true, viewModel.uiState.value.isRoamingControlsExpanded)

            viewModel.onAction(MapAction.StopRoaming)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(false, viewModel.uiState.value.isRoamingControlsExpanded)
        }

    @Test
    fun `PauseRoaming calls roamingRepository pauseRoaming`() =
        runTest {
            viewModel.onAction(MapAction.PauseRoaming)
            verify { roamingRepository.pauseRoaming() }
        }

    @Test
    fun `ResumeRoaming calls roamingRepository resumeRoaming`() =
        runTest {
            viewModel.onAction(MapAction.ResumeRoaming)
            verify { roamingRepository.resumeRoaming() }
        }

    // Roaming draft mutations

    @Test
    fun `UpdateRoamingRadius updates draft radiusMeters`() =
        runTest {
            viewModel.onAction(MapAction.OpenRoamingSheet)
            viewModel.onAction(MapAction.UpdateRoamingRadius(500.0))
            assertEquals(
                500.0,
                viewModel.uiState.value.roamingDraft
                    ?.radiusMeters,
            )
        }

    @Test
    fun `UpdateRoamingDistance updates draft distanceMeters`() =
        runTest {
            viewModel.onAction(MapAction.OpenRoamingSheet)
            viewModel.onAction(MapAction.UpdateRoamingDistance(2000.0))
            assertEquals(
                2000.0,
                viewModel.uiState.value.roamingDraft
                    ?.distanceMeters,
            )
        }

    @Test
    fun `MinimizeRoamingSheet sets isRoamingSheetMinimized true`() =
        runTest {
            viewModel.onAction(MapAction.OpenRoamingSheet)
            viewModel.onAction(MapAction.MinimizeRoamingSheet)
            assertEquals(true, viewModel.uiState.value.isRoamingSheetMinimized)
        }

    @Test
    fun `ExpandRoamingSheet clears isRoamingSheetMinimized`() =
        runTest {
            viewModel.onAction(MapAction.OpenRoamingSheet)
            viewModel.onAction(MapAction.MinimizeRoamingSheet)
            viewModel.onAction(MapAction.ExpandRoamingSheet)
            assertEquals(false, viewModel.uiState.value.isRoamingSheetMinimized)
        }

    // Favorites interaction tests

    @Test
    fun `SelectFavorite when not spoofing closes favorites sheet`() =
        runTest {
            val favorite = FavoriteLocation("1", "Tokyo", LatLng(35.6762, 139.6503), 0L)
            viewModel.onAction(MapAction.OpenFavoritesPicker)
            viewModel.onAction(MapAction.SelectFavorite(favorite))
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(false, viewModel.uiState.value.showFavoritesSheet)
        }

    @Test
    fun `SelectFavorite when spoofing sets favoriteTarget and cameraTarget`() =
        runTest {
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val favorite = FavoriteLocation("1", "Tokyo", LatLng(35.6762, 139.6503), 0L)
            viewModel.onAction(MapAction.SelectFavorite(favorite))

            assertEquals(favorite, viewModel.uiState.value.favoriteTarget)
            assertEquals(favorite.position, viewModel.uiState.value.pendingCameraTarget)
        }

    @Test
    fun `DeselectFavorite clears favoriteTarget`() =
        runTest {
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.SelectFavorite(FavoriteLocation("1", "X", LatLng(1.0, 2.0), 0L)))
            viewModel.onAction(MapAction.DeselectFavorite)

            assertNull(viewModel.uiState.value.favoriteTarget)
        }

    // Misc actions

    @Test
    fun `addRecentSearch calls settingsRepository addRecentSearch`() =
        runTest {
            viewModel.addRecentSearch("Tokyo", 35.6762, 139.6503)
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify { settingsRepository.addRecentSearch("Tokyo", 35.6762, 139.6503) }
        }

    @Test
    fun `SaveCurrentLocation with current position calls favoriteRepository`() =
        runTest {
            val currentPos = LatLng(35.6762, 139.6503)
            every { locationRepository.currentPosition } returns MutableStateFlow(currentPos)
            coEvery { favoriteRepository.addFavorite(any(), any(), any(), any()) } returns Result.success(Unit)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.SaveCurrentLocation("Tokyo"))
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { favoriteRepository.addFavorite(any(), eq("Tokyo"), eq(currentPos), any()) }
        }

    @Test
    fun `ClearMap stops walk and resets roaming sheet state`() =
        runTest {
            viewModel.onAction(MapAction.OpenRoamingSheet)
            assertEquals(true, viewModel.uiState.value.showRoamingSheet)

            viewModel.onAction(MapAction.ClearMap)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.showRoamingSheet)
            assertNull(viewModel.uiState.value.roamingDraft)
        }
}
