package com.locationjoystick.core.location

import android.content.Context
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression test for the bug where starting a new walk-to while an ephemeral replay
 * (built via "add next point") was active would not cancel the replay, causing the
 * previous route to continue instead of starting the new walk.
 *
 * Scenario: walkTo(target1) → addEphemeralWaypoint ×5 → walkTo(target2)
 * Expected: ephemeral replay cancelled, new walk to target2 starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapControllerWalkCancellationTest {
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
    fun `walkTo cancels active ephemeral replay and starts new walk`() =
        runTest(UnconfinedTestDispatcher()) {
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
                    // resolveRoute with followRoads=false returns a straight-line pair — mirror
                    // the real implementation so addWaypoint produces a valid waypoint list.
                    coEvery { it.resolveRoute(any(), any(), any(), any()) } answers {
                        listOf(secondArg<LatLng>(), thirdArg<LatLng>())
                    }
                }
            val walkToEngine = WalkToEngine(settingsRepository, locationRepository)
            val walkCoordinator = WalkCoordinator(locationRepository, walkToEngine)
            val ephemeralController =
                EphemeralReplayController(locationRepository, settingsRepository, walkCoordinator, osrmClient)

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
                    appScope = backgroundScope,
                )

            // Step 1: start walk-to (not via roads)
            val start = LatLng(48.8566, 2.3522)
            val target1 = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(start)
            mapController.walkTo(target1)

            assertEquals(target1, locationRepository.walkTarget.value)
            assertEquals(MockMode.WALK_TO, locationRepository.currentMode.value)

            // Step 2: tap "add next point" 5 times — transitions walk → ephemeral replay
            val nextPoints = (1..5).map { i -> LatLng(48.9000 + i * 0.01, 2.3522) }
            for (point in nextPoints) {
                mapController.addEphemeralWaypoint(point)
            }
            assertTrue(
                "Ephemeral waypoints should be populated after 5 add-next-point taps",
                ephemeralController.pendingWaypoints.value.isNotEmpty(),
            )
            assertTrue(
                "walkMode should be EphemeralReplay",
                mapController.sharedState.value.walkMode is WalkMode.EphemeralReplay,
            )

            // Step 3: pick a new destination and tap "Walk here"
            val target2 = LatLng(48.8700, 2.3000)
            mapController.walkTo(target2)

            // Step 4: ephemeral replay must be fully cancelled; new walk to target2 must be active
            assertTrue(
                "Ephemeral waypoints must be cleared when new walk-to starts",
                ephemeralController.pendingWaypoints.value.isEmpty(),
            )
            assertEquals(
                "Walk target must be the new destination",
                target2,
                locationRepository.walkTarget.value,
            )
            assertEquals(
                "Mode must be WALK_TO",
                MockMode.WALK_TO,
                locationRepository.currentMode.value,
            )
        }
}
