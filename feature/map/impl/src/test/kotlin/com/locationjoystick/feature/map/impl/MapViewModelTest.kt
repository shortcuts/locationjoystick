package com.locationjoystick.feature.map.impl

import android.content.Context
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.Route
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class MapViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var locationRepository: LocationRepository
    private lateinit var routeRepository: RouteRepository
    private lateinit var favoriteRepository: FavoriteRepository
    private lateinit var viewModel: MapViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        routeRepository = mockk()
        favoriteRepository = mockk()

        every { locationRepository.observePosition() } returns MutableStateFlow(null)
        every { locationRepository.observeState() } returns MutableStateFlow(MockLocationState.IDLE)
        every { routeRepository.getRoutes() } returns flowOf(emptyList<Route>())
        every { favoriteRepository.getFavorites() } returns flowOf(emptyList<FavoriteLocation>())

        viewModel = MapViewModel(context, locationRepository, routeRepository, favoriteRepository)
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
            viewModel = MapViewModel(context, locationRepository, routeRepository, favoriteRepository)

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
            viewModel = MapViewModel(context, locationRepository, routeRepository, favoriteRepository)

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
            viewModel = MapViewModel(context, locationRepository, routeRepository, favoriteRepository)

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
            viewModel = MapViewModel(context, locationRepository, routeRepository, favoriteRepository)

            viewModel.onAction(MapAction.TapToTeleport(LatLng(3.0, 4.0)))
            assertEquals(LatLng(3.0, 4.0), viewModel.uiState.value.pendingTapPosition)

            viewModel.onAction(MapAction.StopSpoofing)

            assertNull(viewModel.uiState.value.pendingTapPosition)
        }
}
