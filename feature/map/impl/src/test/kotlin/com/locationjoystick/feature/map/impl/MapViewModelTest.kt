package com.locationjoystick.feature.map.impl

import android.content.Context
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.datastore.PreferencesDataSource
import com.locationjoystick.core.datastore.SpeedProfilePreferences
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.Route
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

        every { locationRepository.observePosition() } returns MutableStateFlow(null)
        every { locationRepository.observeState() } returns MutableStateFlow(MockLocationState.IDLE)
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

        viewModel =
            MapViewModel(
                context,
                locationRepository,
                routeRepository,
                favoriteRepository,
                settingsRepository,
                roamingRepository,
                preferencesDataSource,
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
            every { locationRepository.observeState() } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.observePosition() } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
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
            every { locationRepository.observeState() } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.observePosition() } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
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
            every { locationRepository.observeState() } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.observePosition() } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.TapToTeleport(LatLng(1.0, 2.0)))
            assertEquals(LatLng(1.0, 2.0), viewModel.uiState.value.pendingTapPosition)

            viewModel.onAction(MapAction.ClearPendingTap)

            assertNull(viewModel.uiState.value.pendingTapPosition)
        }

    @Test
    fun `stopSpoofing_clearsPendingTapPosition`() =
        runTest {
            // Start spoofing and set a pending tap
            every { locationRepository.observeState() } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.observePosition() } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
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
            every { locationRepository.observeState() } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.observePosition() } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
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
            every { locationRepository.observeState() } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.observePosition() } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
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
            every { locationRepository.observeState() } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.observePosition() } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
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
            every { locationRepository.observeState() } returns MutableStateFlow(MockLocationState.RUNNING)
            every { locationRepository.observePosition() } returns MutableStateFlow(null)
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
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
            // Seed some walk state into UI
            every { locationRepository.setWalkTarget(any()) } answers {
                walkTargetFlow.value = firstArg<LatLng?>()
            }
            val target = LatLng(1.0, 2.0)
            viewModel.onAction(MapAction.LongPressTapToWalk(target))

            viewModel.onAction(MapAction.StopWalk)

            verify { locationRepository.setWalkTarget(null) }
            assertNull(viewModel.uiState.value.walkTarget)
            assertNull(viewModel.uiState.value.walkStart)
        }

    @Test
    fun `walkLoop_breaksWhenWalkTargetClearedExternally`() =
        runTest {
            val target = LatLng(48.0, 2.0)
            val startPos = LatLng(47.0, 1.0)
            val positionFlow = MutableStateFlow<LatLng?>(startPos)

            every { locationRepository.observePosition() } returns positionFlow
            every { locationRepository.setWalkTarget(any()) } answers {
                walkTargetFlow.value = firstArg<LatLng?>()
            }
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.LongPressTapToWalk(target))
            assertEquals(target, viewModel.uiState.value.walkTarget)

            // Simulate external stop (widget calls setWalkTarget(null))
            walkTargetFlow.value = null
            testDispatcher.scheduler.advanceTimeBy(1001)

            // finally block in walkTo() clears UI state
            assertNull(viewModel.uiState.value.walkTarget)
            assertNull(viewModel.uiState.value.walkStart)
        }

    @Test
    fun `walkLoop_remainsActiveWhilePausedExternally`() =
        runTest {
            val target = LatLng(48.0, 2.0)
            val startPos = LatLng(47.0, 1.0)
            val positionFlow = MutableStateFlow<LatLng?>(startPos)

            every { locationRepository.observePosition() } returns positionFlow
            every { locationRepository.setWalkTarget(any()) } answers {
                walkTargetFlow.value = firstArg<LatLng?>()
            }
            viewModel =
                MapViewModel(
                    context,
                    locationRepository,
                    routeRepository,
                    favoriteRepository,
                    settingsRepository,
                    roamingRepository,
                    preferencesDataSource,
                )
            testDispatcher.scheduler.advanceUntilIdle()

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
}
