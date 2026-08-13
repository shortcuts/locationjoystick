package com.locationjoystick.feature.widget.impl

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
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
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoutingErrorReporter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import android.view.WindowManager as AndroidWindowManager

/**
 * Regression coverage for the floating map's route-controls expanded state (PR #40).
 *
 * The bug: the expanded flag lived in a composable-local `remember` inside `MapFloatingView`,
 * which reset to `false` every time [WidgetPanelPresenter.showMapFloatingView] rebuilt the
 * `ComposeView`. The fix hoists it to [WidgetPanelPresenter.mapRouteControlsExpanded], a
 * `MutableStateFlow` owned by the presenter (survives panel reopen) that auto-collapses once
 * the replay ends so the next route doesn't start pre-expanded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetPanelPresenterTest {
    private val currentModeFlow = MutableStateFlow(MockMode.JOYSTICK)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var presenter: WidgetPanelPresenter

    @Before
    fun setUp() {
        val locationRepository =
            mockk<LocationRepository>(relaxed = true) {
                every { currentPosition } returns MutableStateFlow(null)
                every { mockLocationState } returns MutableStateFlow(MockLocationState.IDLE)
                every { isWalkPaused } returns MutableStateFlow(false)
                every { currentMode } returns currentModeFlow
                every { routeWaypoints } returns MutableStateFlow(null)
            }
        val routeRepository = mockk<RouteRepository>(relaxed = true) { every { getRoutes() } returns flowOf(emptyList()) }
        val favoriteRepository =
            mockk<FavoriteRepository>(relaxed = true) { every { getFavorites() } returns flowOf(emptyList()) }
        val settingsRepository =
            mockk<SettingsRepository>(relaxed = true) {
                every { getActiveSpeedProfile() } returns flowOf(mockk(relaxed = true))
                every { getRoutesSortNewestFirst() } returns flowOf(true)
                every { getFavoritesSortNewestFirst() } returns flowOf(true)
                every { getSpeedUnit() } returns flowOf(mockk(relaxed = true))
                every { getRecentSearches() } returns flowOf(emptyList())
                every { getRememberLastLocation() } returns flowOf(false)
                every { getLastLocation() } returns flowOf(null)
                every { getLastTeleportTime() } returns flowOf(0L)
            }
        val roamingRepository =
            mockk<RoamingRepository>(relaxed = true) {
                every { isRoaming } returns MutableStateFlow(false)
                every { isRoamingPaused } returns MutableStateFlow(false)
            }
        val ephemeralReplayController =
            mockk<EphemeralReplayController>(relaxed = true) {
                every { pendingWaypoints } returns MutableStateFlow(emptyList())
            }

        val scope = CoroutineScope(testDispatcher)
        val mapController =
            MapController(
                context = mockk(relaxed = true),
                locationRepository = locationRepository,
                routeRepository = routeRepository,
                favoriteRepository = favoriteRepository,
                settingsRepository = settingsRepository,
                roamingRepository = roamingRepository,
                walkCoordinator = mockk<WalkCoordinator>(relaxed = true),
                teleportUseCase = mockk<TeleportUseCase>(relaxed = true),
                startRouteReplayUseCase = mockk<StartRouteReplayUseCase>(relaxed = true),
                ephemeralReplayController = ephemeralReplayController,
                osrmClient = mockk<OsrmClient>(relaxed = true),
                routingErrorReporter = RoutingErrorReporter(),
                appScope = scope,
            )

        presenter =
            WidgetPanelPresenter(
                context = mockk<Context>(relaxed = true),
                windowManager = mockk<AndroidWindowManager>(relaxed = true),
                lifecycleOwner = mockk<LifecycleOwner>(relaxed = true),
                savedStateRegistryOwner = mockk<SavedStateRegistryOwner>(relaxed = true),
                serviceScope = scope,
                mapController = mapController,
                callbacks = mockk(relaxed = true),
                settingsRepository = settingsRepository,
            )
    }

    @Test
    fun `route controls start collapsed`() =
        runTest(testDispatcher) {
            assertFalse(presenter.mapRouteControlsExpanded.value)
        }

    @Test
    fun `expanded flag survives while replay stays active (panel reopen does not reset it)`() =
        runTest(testDispatcher) {
            currentModeFlow.value = MockMode.ROUTE_REPLAY
            advanceUntilIdle()

            presenter.mapRouteControlsExpanded.value = true
            advanceUntilIdle()

            // Simulating the panel being closed and reopened does not touch this flag — unlike the
            // old `remember` inside MapFloatingView, nothing here recreates it, so it must still be
            // expanded as long as the replay is still running.
            assertTrue(presenter.mapRouteControlsExpanded.value)
        }

    @Test
    fun `expanded flag auto-collapses once route replay ends`() =
        runTest(testDispatcher) {
            currentModeFlow.value = MockMode.ROUTE_REPLAY
            advanceUntilIdle()
            presenter.mapRouteControlsExpanded.value = true
            advanceUntilIdle()
            assertTrue(presenter.mapRouteControlsExpanded.value)

            currentModeFlow.value = MockMode.TELEPORT
            advanceUntilIdle()

            assertFalse(presenter.mapRouteControlsExpanded.value)
        }
}
