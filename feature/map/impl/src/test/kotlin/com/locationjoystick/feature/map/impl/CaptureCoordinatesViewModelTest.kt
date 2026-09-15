package com.locationjoystick.feature.map.impl

import com.locationjoystick.core.data.CaptureCoordinatesRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.testing.FakePreferencesDataStore
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureCoordinatesViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val routeRepository: RouteRepository = mockk(relaxed = true)
    private lateinit var captureRepository: CaptureCoordinatesRepository
    private lateinit var viewModel: CaptureCoordinatesViewModel

    private val mushroom = LatLng(36.977695, 128.363905)
    private val flower = LatLng(36.982194, 128.370129)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        captureRepository = CaptureCoordinatesRepository(FakePreferencesDataStore())
        viewModel = CaptureCoordinatesViewModel(captureRepository, routeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `defaults to capture off and empty list`() =
        runTest {
            assertFalse(viewModel.uiState.value.captureModeEnabled)
            assertFalse(viewModel.uiState.value.captureEnabled)
            assertFalse(viewModel.uiState.value.jumpEnabled)
            assertTrue(
                viewModel.uiState.value.points
                    .isEmpty(),
            )
            assertFalse(viewModel.uiState.value.canSave)
            assertEquals(CapturePointOrder.PROXIMITY, viewModel.uiState.value.pointOrder)
        }

    @Test
    fun `setCaptureEnabled updates state`() =
        runTest {
            viewModel.setCaptureEnabled(true)
            assertTrue(viewModel.uiState.value.captureEnabled)
        }

    @Test
    fun `setCaptureModeEnabled updates state`() =
        runTest {
            viewModel.setCaptureModeEnabled(true)
            assertTrue(viewModel.uiState.value.captureModeEnabled)
        }

    @Test
    fun `setJumpEnabled updates state`() =
        runTest {
            viewModel.setJumpEnabled(true)
            assertTrue(viewModel.uiState.value.jumpEnabled)
        }

    @Test
    fun `saveRoute writes straight route from captured points`() =
        runTest {
            captureRepository.appendPoint(mushroom)
            captureRepository.appendPoint(flower)
            viewModel.onRouteNameChange("Mushrooms")
            assertTrue(viewModel.uiState.value.canSave)

            viewModel.saveRoute()

            val slot = slot<Route>()
            coVerify { routeRepository.insertRoute(capture(slot)) }
            assertEquals("Mushrooms", slot.captured.name)
            assertEquals(2, slot.captured.waypoints.size)
            assertEquals(mushroom, slot.captured.waypoints[0].position)
            assertEquals(flower, slot.captured.waypoints[1].position)
            assertTrue(viewModel.uiState.value.saved)
        }

    @Test
    fun `saveRoute uses proximity order by default`() =
        runTest {
            val far = LatLng(1.0, 1.0)
            val start = LatLng(0.0, 0.0)
            val near = LatLng(0.05, 0.05)
            captureRepository.appendPoint(far)
            captureRepository.appendPoint(start)
            captureRepository.appendPoint(near)
            viewModel.onRouteNameChange("Loop")

            viewModel.saveRoute()

            val slot = slot<Route>()
            coVerify { routeRepository.insertRoute(capture(slot)) }
            assertEquals(
                listOf(start, near, far),
                slot.captured.waypoints.map { it.position },
            )
            assertEquals(listOf(far, start, near), viewModel.uiState.value.points)
        }

    @Test
    fun `saveRoute keeps capture order when original is selected`() =
        runTest {
            val far = LatLng(1.0, 1.0)
            val start = LatLng(0.0, 0.0)
            val near = LatLng(0.05, 0.05)
            captureRepository.appendPoint(far)
            captureRepository.appendPoint(start)
            captureRepository.appendPoint(near)
            viewModel.onRouteNameChange("Loop")
            viewModel.onPointOrderChange(CapturePointOrder.ORIGINAL)

            viewModel.saveRoute()

            val slot = slot<Route>()
            coVerify { routeRepository.insertRoute(capture(slot)) }
            assertEquals(
                listOf(far, start, near),
                slot.captured.waypoints.map { it.position },
            )
        }

    @Test
    fun `saveRoute without enough points sets error`() =
        runTest {
            viewModel.onRouteNameChange("Nope")
            viewModel.saveRoute()
            assertEquals(R.string.capture_invalid_route, viewModel.uiState.value.saveError)
            coVerify(exactly = 0) { routeRepository.insertRoute(any()) }
        }

    @Test
    fun `rememberPreviousBrowser persists package`() =
        runTest {
            viewModel.rememberPreviousBrowser("com.android.chrome")
            assertEquals("com.android.chrome", captureRepository.previousBrowserPackage.first())
            assertEquals("com.android.chrome", viewModel.uiState.value.previousBrowserPackage)
        }
}
