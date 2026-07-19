package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.data.WalkToEngine
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoutingErrorReporter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test coverage for walk-to natural-arrival cleanup of map polyline / "Add next point" state.
 *
 * Regression test: When walkTo() or walkViaRoads() reaches the target naturally (not cancelled by
 * user), the mode transitions from WALK_TO to TELEPORT, which should trigger cleanup:
 * - routeTrace (map polyline) cleared to null
 * - walkMode set to Idle
 * - "Add next point" state cleared
 *
 * This test validates that the invariant held by observeModeCompletions() in MapController
 * actually fires and resets state correctly on natural arrival.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapControllerTest {
    private val walkProfile = SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = 1.4)

    @Before
    fun setUp() {
        mockkObject(MockLocationIntentBuilder)
        every { MockLocationIntentBuilder.startEphemeralReplay(any(), any(), any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.appendWaypoint(any(), any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.cancelRouteReplay(any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.updatePosition(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(MockLocationIntentBuilder)
    }

    @Test
    fun `plain walkTo natural arrival clears polyline and resets walk mode`() =
        runTest {
            val locationRepository = LocationRepository()
            val settingsRepository =
                mockk<SettingsRepository>(relaxed = true) {
                    every { getActiveSpeedProfile() } returns flowOf(walkProfile)
                    every { getRoutesSortNewestFirst() } returns flowOf(true)
                    every { getFavoritesSortNewestFirst() } returns flowOf(true)
                    every { getSpeedUnit() } returns flowOf(SpeedUnit.KMH)
                    every { getRecentSearches() } returns flowOf(emptyList())
                    every { getRoamingDefaults() } returns flowOf(RoamingDefaults())
                    every { getSettingsSnapshot() } returns emptyFlow()
                    every { getRememberLastLocation() } returns flowOf(false)
                }
            val osrmClient = mockk<OsrmClient>(relaxed = true)
            val walkToEngine = WalkToEngine(settingsRepository, locationRepository)
            val walkCoordinator = WalkCoordinator(locationRepository, walkToEngine)
            val routingErrorReporter = RoutingErrorReporter()
            val ephemeralController =
                EphemeralReplayController(locationRepository, settingsRepository, walkCoordinator, osrmClient, routingErrorReporter)

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
            val teleportUseCase = mockk<TeleportUseCase>(relaxed = true) { every { cooldownsFor(any()) } returns emptyFlow() }
            val startRouteReplayUseCase = mockk<StartRouteReplayUseCase>(relaxed = true)

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
                    startRouteReplayUseCase = startRouteReplayUseCase,
                    ephemeralReplayController = ephemeralController,
                    osrmClient = osrmClient,
                    routingErrorReporter = routingErrorReporter,
                    appScope = backgroundScope,
                )

            // Place target close enough to arrive within a reasonable number of ticks.
            // Target is 0.5m away, which is within WALK_ARRIVAL_THRESHOLD_METERS (1.0m),
            // so arrival should trigger on the first loop iteration.
            val start = LatLng(48.8566, 2.3522)
            val target = LatLng(48.856604498, 2.3522) // ~0.5m north
            locationRepository.setPositionInternal(start)

            // Step 1: Start plain walk-to
            mapController.walkTo(target)

            assertEquals("Mode should be WALK_TO", MockMode.WALK_TO, locationRepository.currentMode.value)
            assertEquals("Walk target should be set", target, locationRepository.walkTarget.value)
            assertNull("routeTrace should be null for plain walk", mapController.sharedState.value.routeTrace)
            assertTrue("walkMode should be Walking", mapController.sharedState.value.walkMode is WalkMode.Walking)

            // Step 2: Advance time to allow the walk loop to start and detect arrival.
            // Even though target is within threshold, we need one tick to run the check.
            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS + 100)
            advanceUntilIdle()

            // Step 3: Verify cleanup happened
            assertEquals(
                "Mode should transition to TELEPORT on arrival",
                MockMode.TELEPORT,
                locationRepository.currentMode.value,
            )
            assertNull("routeTrace should be cleared to null after arrival", mapController.sharedState.value.routeTrace)
            assertTrue(
                "walkMode should be reset to Idle after arrival",
                mapController.sharedState.value.walkMode == WalkMode.Idle,
            )
            assertNull("walkTarget should be cleared after arrival", locationRepository.walkTarget.value)
        }

    @Test
    fun `walkViaRoads natural arrival clears polyline and resets walk mode`() =
        runTest {
            val locationRepository = LocationRepository()
            val settingsRepository =
                mockk<SettingsRepository>(relaxed = true) {
                    every { getActiveSpeedProfile() } returns flowOf(walkProfile)
                    every { getRoutesSortNewestFirst() } returns flowOf(true)
                    every { getFavoritesSortNewestFirst() } returns flowOf(true)
                    every { getSpeedUnit() } returns flowOf(SpeedUnit.KMH)
                    every { getRecentSearches() } returns flowOf(emptyList())
                    every { getRoamingDefaults() } returns flowOf(RoamingDefaults())
                    every { getSettingsSnapshot() } returns emptyFlow()
                    every { getRememberLastLocation() } returns flowOf(false)
                }
            // Mock OSRM to return a simple 2-point route
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.getRoute(any(), any()) } answers {
                        // Return the input waypoints as-is (straight path)
                        Result.success(secondArg<List<LatLng>>())
                    }
                }
            val walkToEngine = WalkToEngine(settingsRepository, locationRepository)
            val walkCoordinator = WalkCoordinator(locationRepository, walkToEngine)
            val routingErrorReporter = RoutingErrorReporter()
            val ephemeralController =
                EphemeralReplayController(locationRepository, settingsRepository, walkCoordinator, osrmClient, routingErrorReporter)

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
            val teleportUseCase = mockk<TeleportUseCase>(relaxed = true) { every { cooldownsFor(any()) } returns emptyFlow() }
            val startRouteReplayUseCase = mockk<StartRouteReplayUseCase>(relaxed = true)

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
                    startRouteReplayUseCase = startRouteReplayUseCase,
                    ephemeralReplayController = ephemeralController,
                    osrmClient = osrmClient,
                    routingErrorReporter = routingErrorReporter,
                    appScope = backgroundScope,
                )

            val start = LatLng(48.8566, 2.3522)
            val target = LatLng(48.856604498, 2.3522) // ~0.5m north
            locationRepository.setPositionInternal(start)

            // Step 1: Start walk-via-roads
            mapController.walkViaRoads(target)

            // Wait for OSRM call to complete and walk to fully execute and complete
            // (target is close, so it should complete within a few ticks)
            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS * 2)
            advanceUntilIdle()

            // Step 2: Verify cleanup happened
            // By now, the walk should have started, reached the target, and transitioned to TELEPORT.
            // The finally block in launchWalkAlongRoute clears walkTarget, so it will be null here.
            assertEquals(
                "Mode should transition to TELEPORT on arrival",
                MockMode.TELEPORT,
                locationRepository.currentMode.value,
            )
            assertNull("routeTrace should be cleared to null after arrival", mapController.sharedState.value.routeTrace)
            assertTrue(
                "walkMode should be reset to Idle after arrival",
                mapController.sharedState.value.walkMode == WalkMode.Idle,
            )
            assertNull("walkTarget should be cleared after arrival", locationRepository.walkTarget.value)
        }
}
