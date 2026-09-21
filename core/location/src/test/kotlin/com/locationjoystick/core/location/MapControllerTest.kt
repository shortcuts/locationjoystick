package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RealLocationRepository
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
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
                    every { getRoutesSortMode() } returns flowOf(com.locationjoystick.core.model.SavedItemSortMode.NEWEST_FIRST)
                    every { getFavoritesSortMode() } returns flowOf(com.locationjoystick.core.model.SavedItemSortMode.NEWEST_FIRST)
                    every { getSpeedUnit() } returns flowOf(SpeedUnit.KMH)
                    every { getRecentSearches() } returns flowOf(emptyList())
                    every { getRoamingDefaults() } returns flowOf(RoamingDefaults())
                    every { getSettingsSnapshot() } returns emptyFlow()
                    every { getRememberLastLocation() } returns flowOf(false)
                }
            val osrmClient = mockk<OsrmClient>(relaxed = true)
            val walkToEngine = WalkToEngine(settingsRepository, locationRepository)
            val walkCoordinator = WalkCoordinator(locationRepository, walkToEngine)
            val routingErrorReporter = RoutingErrorReporter(mockk<android.content.Context>(relaxed = true))
            val ephemeralController =
                EphemeralReplayController(
                    locationRepository,
                    settingsRepository,
                    walkCoordinator,
                    osrmClient,
                    routingErrorReporter,
                )

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
            val teleportUseCase =
                mockk<TeleportUseCase>(relaxed = true) { every { cooldownsFor(any()) } returns emptyFlow() }
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
                    realLocationRepository =
                        mockk<RealLocationRepository> {
                            every { lastKnownRealPosition() } returns null
                            every { hasFinePermission() } returns false
                        },
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
                    every { getRoutesSortMode() } returns flowOf(com.locationjoystick.core.model.SavedItemSortMode.NEWEST_FIRST)
                    every { getFavoritesSortMode() } returns flowOf(com.locationjoystick.core.model.SavedItemSortMode.NEWEST_FIRST)
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
            val routingErrorReporter = RoutingErrorReporter(mockk<android.content.Context>(relaxed = true))
            val ephemeralController =
                EphemeralReplayController(
                    locationRepository,
                    settingsRepository,
                    walkCoordinator,
                    osrmClient,
                    routingErrorReporter,
                )

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
            val teleportUseCase =
                mockk<TeleportUseCase>(relaxed = true) { every { cooldownsFor(any()) } returns emptyFlow() }
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
                    realLocationRepository =
                        mockk<RealLocationRepository> {
                            every { lastKnownRealPosition() } returns null
                            every { hasFinePermission() } returns false
                        },
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

    @Test
    fun `walkViaRoads flips isRoadRouteFetchInFlight true during OSRM fetch and false after success`() =
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
            val fetchGate = CompletableDeferred<Unit>()
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.getRoute(any(), any()) } coAnswers {
                        fetchGate.await()
                        Result.success(secondArg<List<LatLng>>())
                    }
                }
            val walkToEngine = WalkToEngine(settingsRepository, locationRepository)
            val walkCoordinator = WalkCoordinator(locationRepository, walkToEngine)
            val routingErrorReporter = RoutingErrorReporter(mockk<android.content.Context>(relaxed = true))
            val ephemeralController =
                EphemeralReplayController(
                    locationRepository,
                    settingsRepository,
                    walkCoordinator,
                    osrmClient,
                    routingErrorReporter,
                )

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
            val teleportUseCase =
                mockk<TeleportUseCase>(relaxed = true) { every { cooldownsFor(any()) } returns emptyFlow() }
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
                    realLocationRepository =
                        mockk<RealLocationRepository> {
                            every { lastKnownRealPosition() } returns null
                            every { hasFinePermission() } returns false
                        },
                    startRouteReplayUseCase = startRouteReplayUseCase,
                    ephemeralReplayController = ephemeralController,
                    osrmClient = osrmClient,
                    routingErrorReporter = routingErrorReporter,
                    appScope = backgroundScope,
                )

            val start = LatLng(48.8566, 2.3522)
            val target = LatLng(48.856604498, 2.3522)
            locationRepository.setPositionInternal(start)

            assertFalse(
                "flag should be false before any fetch starts",
                locationRepository.isRoadRouteFetchInFlight.value,
            )

            mapController.walkViaRoads(target)
            // runCurrent(), not advanceUntilIdle(): backgroundScope's queued work isn't picked up
            // by advanceUntilIdle() in this project's resolved coroutines-test version.
            runCurrent()

            assertTrue(
                "flag should be true while the OSRM fetch is in flight",
                locationRepository.isRoadRouteFetchInFlight.value,
            )

            fetchGate.complete(Unit)
            runCurrent()

            assertFalse(
                "flag should be false again once the fetch completes",
                locationRepository.isRoadRouteFetchInFlight.value,
            )
        }

    @Test
    fun `walkViaRoads clears isRoadRouteFetchInFlight even when the OSRM fetch fails`() =
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
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.getRoute(any(), any()) } returns Result.failure(RuntimeException("boom"))
                }
            val walkToEngine = WalkToEngine(settingsRepository, locationRepository)
            val walkCoordinator = WalkCoordinator(locationRepository, walkToEngine)
            val routingErrorReporter = RoutingErrorReporter(mockk<android.content.Context>(relaxed = true))
            val ephemeralController =
                EphemeralReplayController(
                    locationRepository,
                    settingsRepository,
                    walkCoordinator,
                    osrmClient,
                    routingErrorReporter,
                )

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
            val teleportUseCase =
                mockk<TeleportUseCase>(relaxed = true) { every { cooldownsFor(any()) } returns emptyFlow() }
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
                    realLocationRepository =
                        mockk<RealLocationRepository> {
                            every { lastKnownRealPosition() } returns null
                            every { hasFinePermission() } returns false
                        },
                    startRouteReplayUseCase = startRouteReplayUseCase,
                    ephemeralReplayController = ephemeralController,
                    osrmClient = osrmClient,
                    routingErrorReporter = routingErrorReporter,
                    appScope = backgroundScope,
                )

            val start = LatLng(48.8566, 2.3522)
            val target = LatLng(48.8567, 2.3523)
            locationRepository.setPositionInternal(start)

            mapController.walkViaRoads(target)
            runCurrent()

            assertFalse(
                "flag should be reset to false even after a failed OSRM fetch",
                locationRepository.isRoadRouteFetchInFlight.value,
            )
        }

    @Test
    fun `restoreLastLocation seeds the position from the real last-known fix when nothing is remembered`() =
        runTest {
            val real = mockk<RealLocationRepository> { every { lastKnownRealPosition() } returns LatLng(1.0, 2.0) }
            val locationRepository = LocationRepository()
            val controller = buildRestoreController(locationRepository, real, backgroundScope)

            controller.restoreLastLocationIfNeeded()
            runCurrent()

            assertEquals(LatLng(1.0, 2.0), locationRepository.currentPosition.value)
        }

    @Test
    fun `restoreLastLocation prefers the remembered location over the real fix`() =
        runTest {
            val real = mockk<RealLocationRepository> { every { lastKnownRealPosition() } returns LatLng(1.0, 2.0) }
            val locationRepository = LocationRepository()
            val controller = buildRestoreController(locationRepository, real, backgroundScope, saved = LatLng(3.0, 4.0))

            controller.restoreLastLocationIfNeeded()
            runCurrent()

            assertEquals(LatLng(3.0, 4.0), locationRepository.currentPosition.value)
        }

    @Test
    fun `restoreLastLocation leaves position unset when there is no fix`() =
        runTest {
            val real =
                mockk<RealLocationRepository> {
                    every { lastKnownRealPosition() } returns null
                    every { hasFinePermission() } returns false
                }
            val locationRepository = LocationRepository()
            val controller = buildRestoreController(locationRepository, real, backgroundScope)

            controller.restoreLastLocationIfNeeded()
            runCurrent()

            assertNull(locationRepository.currentPosition.value)
        }

    @Test
    fun `restoreLastLocation falls back to the app default when permitted but no fix is found`() =
        runTest {
            val real =
                mockk<RealLocationRepository> {
                    every { lastKnownRealPosition() } returns null
                    every { hasFinePermission() } returns true
                    coEvery { getCurrentPosition() } returns Result.failure(IllegalStateException("no fix"))
                }
            val locationRepository = LocationRepository()
            val controller = buildRestoreController(locationRepository, real, backgroundScope)

            controller.restoreLastLocationIfNeeded()
            runCurrent()

            assertEquals(
                LatLng(AppConstants.MapConstants.DEFAULT_LAT, AppConstants.MapConstants.DEFAULT_LON),
                locationRepository.currentPosition.value,
            )
        }

    @Test
    fun `restoreLastLocation uses a fresh real fix when there is no last-known fix`() =
        runTest {
            val real =
                mockk<RealLocationRepository> {
                    every { lastKnownRealPosition() } returns null
                    every { hasFinePermission() } returns true
                    coEvery { getCurrentPosition() } returns Result.success(LatLng(5.0, 6.0))
                }
            val locationRepository = LocationRepository()
            val controller = buildRestoreController(locationRepository, real, backgroundScope)

            controller.restoreLastLocationIfNeeded()
            runCurrent()

            assertEquals(LatLng(5.0, 6.0), locationRepository.currentPosition.value)
        }

    @Test
    fun `restoreLastLocation shows the default at once and keeps a position moved before the fix arrives`() =
        runTest {
            val fix = CompletableDeferred<Result<LatLng>>()
            val real =
                mockk<RealLocationRepository> {
                    every { lastKnownRealPosition() } returns null
                    every { hasFinePermission() } returns true
                    coEvery { getCurrentPosition() } coAnswers { fix.await() }
                }
            val locationRepository = LocationRepository()
            val controller = buildRestoreController(locationRepository, real, backgroundScope)

            controller.restoreLastLocationIfNeeded()
            runCurrent()
            assertEquals(
                LatLng(AppConstants.MapConstants.DEFAULT_LAT, AppConstants.MapConstants.DEFAULT_LON),
                locationRepository.currentPosition.value,
            )

            locationRepository.setPositionInternal(LatLng(7.0, 8.0))
            fix.complete(Result.success(LatLng(5.0, 6.0)))
            runCurrent()

            assertEquals(LatLng(7.0, 8.0), locationRepository.currentPosition.value)
        }

    @Test
    fun `restoreLastLocation overlapping calls run one lookup and a later call retries`() =
        runTest {
            val real =
                mockk<RealLocationRepository> {
                    every { lastKnownRealPosition() } returns null
                    every { hasFinePermission() } returns false
                }
            val controller = buildRestoreController(LocationRepository(), real, backgroundScope)

            controller.restoreLastLocationIfNeeded() // overlaps the restore started by init
            runCurrent()
            verify(exactly = 1) { real.lastKnownRealPosition() }

            controller.restoreLastLocationIfNeeded() // first restore finished with no fix: retry runs
            runCurrent()
            verify(exactly = 2) { real.lastKnownRealPosition() }
        }

    private fun buildRestoreController(
        locationRepository: LocationRepository,
        realLocationRepository: RealLocationRepository,
        scope: kotlinx.coroutines.CoroutineScope,
        saved: LatLng? = null,
    ): MapController {
        val settingsRepository =
            mockk<SettingsRepository>(relaxed = true) {
                every { getActiveSpeedProfile() } returns flowOf(walkProfile)
                every { getRoutesSortMode() } returns flowOf(com.locationjoystick.core.model.SavedItemSortMode.NEWEST_FIRST)
                every { getFavoritesSortMode() } returns flowOf(com.locationjoystick.core.model.SavedItemSortMode.NEWEST_FIRST)
                every { getSpeedUnit() } returns flowOf(SpeedUnit.KMH)
                every { getRecentSearches() } returns flowOf(emptyList())
                every { getRoamingDefaults() } returns flowOf(RoamingDefaults())
                every { getSettingsSnapshot() } returns emptyFlow()
                every { getRememberLastLocation() } returns flowOf(saved != null)
                every { getLastLocation() } returns flowOf(saved)
            }
        val osrmClient = mockk<OsrmClient>(relaxed = true)
        val walkCoordinator = WalkCoordinator(locationRepository, WalkToEngine(settingsRepository, locationRepository))
        val routingErrorReporter = RoutingErrorReporter(mockk<Context>(relaxed = true))
        val context = mockk<Context>(relaxed = true)
        val roamingRepository =
            mockk<RoamingRepository>(relaxed = true) {
                every { isRoaming } returns MutableStateFlow(false)
                every { isRoamingPaused } returns MutableStateFlow(false)
            }
        return MapController(
            context = context,
            locationRepository = locationRepository,
            routeRepository = mockk<RouteRepository>(relaxed = true) { every { getRoutes() } returns emptyFlow() },
            favoriteRepository =
                mockk<FavoriteRepository>(relaxed = true) { every { getFavorites() } returns flowOf(emptyList()) },
            settingsRepository = settingsRepository,
            roamingRepository = roamingRepository,
            walkCoordinator = walkCoordinator,
            teleportUseCase = mockk<TeleportUseCase>(relaxed = true) { every { cooldownsFor(any()) } returns emptyFlow() },
            realLocationRepository = realLocationRepository,
            startRouteReplayUseCase = mockk<StartRouteReplayUseCase>(relaxed = true),
            ephemeralReplayController =
                EphemeralReplayController(locationRepository, settingsRepository, walkCoordinator, osrmClient, routingErrorReporter),
            osrmClient = osrmClient,
            routingErrorReporter = routingErrorReporter,
            appScope = scope,
        )
    }
}
