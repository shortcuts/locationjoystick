package com.locationjoystick.feature.map.impl

import android.content.Context
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.datastore.PreferencesDataSource
import com.locationjoystick.core.datastore.SpeedProfilePreferences
import com.locationjoystick.core.location.EphemeralReplayController
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.routing.OsrmClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    private lateinit var preferencesDataSource: PreferencesDataSource
    private lateinit var walkCoordinator: WalkCoordinator
    private lateinit var teleportUseCase: TeleportUseCase
    private lateinit var ephemeralReplayController: EphemeralReplayController
    private lateinit var osrmClient: OsrmClient
    private lateinit var viewModel: MapViewModel

    private val walkTargetFlow = MutableStateFlow<LatLng?>(null)
    private val isWalkPausedFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        routeRepository = mockk()
        favoriteRepository = mockk()
        settingsRepository = mockk(relaxed = true)
        roamingRepository = mockk(relaxed = true)
        preferencesDataSource = mockk(relaxed = true)
        walkCoordinator = mockk(relaxed = true)
        teleportUseCase = mockk(relaxed = true)
        ephemeralReplayController = mockk(relaxed = true)
        osrmClient = mockk(relaxed = true)

        every { locationRepository.currentPosition } returns MutableStateFlow(null)
        every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.IDLE)
        every { locationRepository.isWalkPaused } returns isWalkPausedFlow
        every { locationRepository.walkTarget } returns walkTargetFlow
        every { locationRepository.currentMode } returns MutableStateFlow(MockMode.JOYSTICK)
        every { locationRepository.routeWaypoints } returns MutableStateFlow(null)
        every { routeRepository.getRoutes() } returns flowOf(emptyList<Route>())
        every { favoriteRepository.getFavorites() } returns flowOf(emptyList<FavoriteLocation>())
        every { roamingRepository.isRoaming } returns MutableStateFlow(false)
        every { preferencesDataSource.getRoamingDefaults() } returns
            flowOf(
                com.locationjoystick.core.model
                    .RoamingDefaults(),
            )
        every { preferencesDataSource.getSpeedProfiles() } returns
            flowOf(
                SpeedProfilePreferences(
                    walkSpeedMs = 1.39,
                    runSpeedMs = 3.0,
                    bikeSpeedMs = 6.0,
                    activeProfileId = "walk",
                ),
            )
        every { settingsRepository.getActiveSpeedProfile() } returns
            flowOf(SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = 1.39))
        every { settingsRepository.getRememberLastLocation() } returns flowOf(false)
        every { settingsRepository.getLastLocation() } returns flowOf(null)

        viewModel =
            MapViewModel(
                context,
                locationRepository,
                routeRepository,
                favoriteRepository,
                settingsRepository,
                roamingRepository,
                preferencesDataSource,
                walkCoordinator,
                teleportUseCase,
                ephemeralReplayController,
                osrmClient,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
            // Set state to RUNNING so isSpoofing == true
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            val position = LatLng(48.8566, 2.3522)
            viewModel.onAction(MapAction.TapToTeleport(position))

            assertEquals(position, viewModel.uiState.value.pendingTapPosition)
        }

    @Test
    fun `confirmTeleport_teleportsAndClearsPending`() =
        runTest {
            // Start spoofing so TapToTeleport sets pending
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            val position = LatLng(48.8566, 2.3522)
            viewModel.onAction(MapAction.TapToTeleport(position))
            assertEquals(position, viewModel.uiState.value.pendingTapPosition)

            viewModel.onAction(MapAction.ConfirmTeleport(position))

            assertNull(viewModel.uiState.value.pendingTapPosition)
        }

    @Test
    fun `clearPendingTap_setsPendingToNull`() =
        runTest {
            // Set pending position directly via spoofing tap
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.TapToTeleport(LatLng(1.0, 2.0)))
            assertEquals(LatLng(1.0, 2.0), viewModel.uiState.value.pendingTapPosition)

            viewModel.onAction(MapAction.ClearPendingTap)

            assertNull(viewModel.uiState.value.pendingTapPosition)
        }

    @Test
    fun `StopSpoofing action sends stop intent to service`() =
        runTest {
            viewModel.onAction(MapAction.StopSpoofing)

            // Service must be stopped via intent so MockLocationService.stopSpoofing() runs
            // inside the service scope — avoids the stuck-start regression where the service
            // keeps running with _state=RUNNING and ignores the next ACTION_START.
            verify { context.startService(any()) }
        }

    @Test
    fun `StopSpoofing action does not call locationRepository stopSpoofing directly`() =
        runTest {
            viewModel.onAction(MapAction.StopSpoofing)

            // Repository state is managed by MockLocationService, not by the ViewModel.
            // Direct calls here raced with serviceScope.cancel in onDestroy and left
            // mockLocationState stuck at RUNNING.
            verify(exactly = 0) { locationRepository.stopSpoofing() }
        }

    @Test
    fun `stopSpoofing_clearsPendingTapPosition`() =
        runTest {
            // Start spoofing and set a pending tap
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.TapToTeleport(LatLng(3.0, 4.0)))
            assertEquals(LatLng(3.0, 4.0), viewModel.uiState.value.pendingTapPosition)

            viewModel.onAction(MapAction.StopSpoofing)

            assertNull(viewModel.uiState.value.pendingTapPosition)
        }

    @Test
    fun `tapToTeleport_preservesExactCoordinates`() =
        runTest {
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
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
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
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
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
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
    fun `clearPendingTap_actuallyClears`() =
        runTest {
            every { locationRepository.mockLocationState } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            val position = LatLng(10.5, 20.5)
            viewModel.onAction(MapAction.TapToTeleport(position))
            assertEquals(position, viewModel.uiState.value.pendingTapPosition)

            viewModel.onAction(MapAction.ClearPendingTap)
            assertNull(viewModel.uiState.value.pendingTapPosition)
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

            viewModel.onAction(MapAction.StopWalk)

            verify { walkCoordinator.cancel() }
            assertNull(viewModel.uiState.value.walkTarget)
            assertNull(viewModel.uiState.value.walkStart)
        }

    @Test
    fun `walkLoop_breaksWhenWalkTargetClearedExternally`() =
        runTest {
            val target = LatLng(48.0, 2.0)

            viewModel.onAction(MapAction.LongPressTapToWalk(target))
            assertEquals(target, viewModel.uiState.value.walkTarget)

            // StopWalk (e.g. triggered by widget) cancels coordinator and clears UI state
            viewModel.onAction(MapAction.StopWalk)

            verify { walkCoordinator.cancel() }
            assertNull(viewModel.uiState.value.walkTarget)
            assertNull(viewModel.uiState.value.walkStart)
        }

    @Test
    fun `walkLoop_remainsActiveWhilePausedExternally`() =
        runTest {
            val target = LatLng(48.0, 2.0)

            viewModel.onAction(MapAction.LongPressTapToWalk(target))
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

            every { locationRepository.currentPosition } returns MutableStateFlow(currentPos)
            coEvery { ephemeralReplayController.addWaypoint(any(), any(), any(), any(), any(), any()) } returns
                listOf(currentPos, walkTarget, newPoint)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
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
            coEvery { ephemeralReplayController.addWaypoint(any(), any(), any(), any(), any(), any()) } returnsMany
                listOf(list3, list4)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
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

            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
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

            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            verify(exactly = 0) { locationRepository.setPositionInternal(any()) }
        }

    @Test
    fun `init_doesNotRestoreLastLocation_whenRememberDisabled`() =
        runTest {
            every { locationRepository.currentPosition } returns MutableStateFlow(null)
            every { settingsRepository.getRememberLastLocation() } returns flowOf(false)
            every { settingsRepository.getLastLocation() } returns flowOf(LatLng(48.8566, 2.3522))

            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            verify(exactly = 0) { locationRepository.setPositionInternal(any()) }
        }

    @Test
    fun `stopWalk_clearsEphemeralWaypoints`() =
        runTest {
            val currentPos = LatLng(48.8, 2.3)
            val walkTarget = LatLng(48.9, 2.4)
            val extra = LatLng(49.0, 2.5)

            every { locationRepository.currentPosition } returns MutableStateFlow(currentPos)
            coEvery { ephemeralReplayController.addWaypoint(any(), any(), any(), any(), any(), any()) } returns
                listOf(currentPos, walkTarget, extra)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                    walkCoordinator,
                    teleportUseCase,
                    ephemeralReplayController,
                    osrmClient,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.LongPressTapToWalk(walkTarget))
            viewModel.onAction(MapAction.AddEphemeralWaypoint(extra))
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(3, viewModel.uiState.value.ephemeralWaypoints.size)

            viewModel.onAction(MapAction.StopWalk)

            val state = viewModel.uiState.value
            assertEquals(emptyList<LatLng>(), state.ephemeralWaypoints)
            assertNull(state.walkTarget)
        }
}
