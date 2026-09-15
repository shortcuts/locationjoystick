package com.locationjoystick.feature.routes.impl

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoutingErrorReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class PasteCoordinatesViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val routeRepository: RouteRepository = mockk(relaxed = true)
    private val osrmClient: OsrmClient = mockk(relaxed = true)
    private val routingErrorReporter: RoutingErrorReporter = mockk(relaxed = true)
    private lateinit var viewModel: PasteCoordinatesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = PasteCoordinatesViewModel(routeRepository, osrmClient, routingErrorReporter)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty paste reports no valid coordinates`() {
        viewModel.loadPoints()
        assertEquals("No valid coordinates found in that text.", viewModel.uiState.value.loadError)
        assertTrue(
            viewModel.uiState.value.cleanedPoints
                .isEmpty(),
        )
    }

    @Test
    fun `messy paste loads cleaned points`() {
        viewModel.onPasteTextChange("1️⃣ 64.147609, -21.922327\n2. 📍 64.152840, -21.932219")
        viewModel.loadPoints()
        assertEquals(2, viewModel.uiState.value.cleanedPoints.size)
        assertEquals(null, viewModel.uiState.value.loadError)
    }

    @Test
    fun `planting radius defaults to 35 meters`() {
        assertEquals(
            AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS,
            viewModel.uiState.value.plantingRadiusMeters,
            0.0,
        )
    }

    @Test
    fun `as-is with one point cannot build`() {
        viewModel.onPasteTextChange("51.5, 0.0")
        viewModel.loadPoints()
        viewModel.buildPreview()
        assertEquals("Need at least 2 points for this mode.", viewModel.uiState.value.buildError)
        assertTrue(
            viewModel.uiState.value.previewWaypoints
                .isEmpty(),
        )
    }

    @Test
    fun `as-is preview keeps cleaned points and resolves STRAIGHT`() =
        runTest {
            viewModel.onPasteTextChange("51.5, 0.0\n48.8, 2.3")
            viewModel.loadPoints()
            viewModel.buildPreview()
            advanceUntilIdle()
            assertEquals(
                listOf(LatLng(51.5, 0.0), LatLng(48.8, 2.3)),
                viewModel.uiState.value.previewWaypoints,
            )
            assertEquals(RouteType.STRAIGHT, viewModel.uiState.value.resolvedRouteType)
        }

    @Test
    fun `planting with one point builds a closed circle`() =
        runTest {
            viewModel.onPasteTextChange("35.68, 139.76")
            viewModel.loadPoints()
            viewModel.onBuildModeChange(PasteBuildMode.PLANTING)
            viewModel.buildPreview()
            advanceUntilIdle()
            val preview = viewModel.uiState.value.previewWaypoints
            assertTrue(preview.size >= 9)
            assertEquals(preview.first(), preview.last())
            assertEquals(RouteType.STRAIGHT, viewModel.uiState.value.resolvedRouteType)
        }

    @Test
    fun `walkable mode resolves GUIDED and uses OSRM`() =
        runTest {
            val from = LatLng(51.5, 0.0)
            val to = LatLng(51.51, 0.01)
            val dense = listOf(from, LatLng(51.505, 0.005), to)
            coEvery {
                osrmClient.resolveRoute(OsrmClient.PROFILE_FOOT, from, to, true, any())
            } returns dense
            viewModel.onPasteTextChange("51.5, 0.0\n51.51, 0.01")
            viewModel.loadPoints()
            viewModel.onBuildModeChange(PasteBuildMode.WALKABLE)
            viewModel.buildPreview()
            advanceUntilIdle()
            assertEquals(dense, viewModel.uiState.value.previewWaypoints)
            assertEquals(RouteType.GUIDED, viewModel.uiState.value.resolvedRouteType)
            verify { routingErrorReporter.reportRoadFollowingFallbacks(0, 1) }
        }

    @Test
    fun `planting via roads resolves GUIDED`() {
        viewModel.onBuildModeChange(PasteBuildMode.PLANTING)
        viewModel.onPlantingTravelChange(PlantingTravel.ROADS)
        assertEquals(RouteType.GUIDED, viewModel.uiState.value.resolvedRouteType)
    }

    @Test
    fun `planting via roads calls OSRM between circles`() =
        runTest {
            coEvery { osrmClient.resolveRoute(any(), any(), any(), true, any()) } answers {
                val from = invocation.args[1] as LatLng
                val to = invocation.args[2] as LatLng
                listOf(from, to)
            }
            viewModel.onPasteTextChange("0.0, 0.0\n0.02, 0.0")
            viewModel.loadPoints()
            viewModel.onBuildModeChange(PasteBuildMode.PLANTING)
            viewModel.onPlantingTravelChange(PlantingTravel.ROADS)
            viewModel.buildPreview()
            advanceUntilIdle()
            coVerify(exactly = 1) { osrmClient.resolveRoute(OsrmClient.PROFILE_FOOT, any(), any(), true, any()) }
            assertTrue(viewModel.uiState.value.previewWaypoints.size > 10)
            assertEquals(RouteType.GUIDED, viewModel.uiState.value.resolvedRouteType)
        }

    @Test
    fun `save inserts a route with preview waypoints`() =
        runTest {
            coEvery { routeRepository.insertRoute(any()) } returns Result.success(Unit)
            viewModel.onPasteTextChange("51.5, 0.0\n48.8, 2.3")
            viewModel.loadPoints()
            viewModel.buildPreview()
            advanceUntilIdle()
            viewModel.saveRoute("  Morning loop  ")
            advanceUntilIdle()
            val routeSlot = slot<com.locationjoystick.core.model.Route>()
            coVerify { routeRepository.insertRoute(capture(routeSlot)) }
            assertEquals("Morning loop", routeSlot.captured.name)
            assertEquals(2, routeSlot.captured.waypoints.size)
            assertEquals(RouteType.STRAIGHT, routeSlot.captured.routeType)
            assertTrue(viewModel.uiState.value.saved)
        }

    @Test
    fun `proximity order is used for as-is build`() =
        runTest {
            viewModel.onPasteTextChange("1.0, 1.0\n0.0, 0.0\n0.05, 0.05")
            viewModel.loadPoints()
            viewModel.onPointOrderChange(PastePointOrder.PROXIMITY)
            viewModel.buildPreview()
            advanceUntilIdle()
            assertEquals(
                listOf(LatLng(0.0, 0.0), LatLng(0.05, 0.05), LatLng(1.0, 1.0)),
                viewModel.uiState.value.previewWaypoints,
            )
        }

    @Test
    fun `blank save name is ignored`() =
        runTest {
            viewModel.onPasteTextChange("51.5, 0.0\n48.8, 2.3")
            viewModel.loadPoints()
            viewModel.buildPreview()
            advanceUntilIdle()
            viewModel.saveRoute("   ")
            coVerify(exactly = 0) { routeRepository.insertRoute(any()) }
            assertFalse(viewModel.uiState.value.saved)
        }
}
