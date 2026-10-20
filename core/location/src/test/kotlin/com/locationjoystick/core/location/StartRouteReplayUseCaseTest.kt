package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteStartConfig
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StartRouteReplayUseCaseTest {
    private val context: Context = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val locationRepository = LocationRepository()
    private val routeRepository: RouteRepository = mockk(relaxed = true)
    private val teleportUseCase: TeleportUseCase = mockk(relaxed = true)
    private lateinit var useCase: StartRouteReplayUseCase

    private val start = LatLng(2.0, 2.0)
    private val end = LatLng(3.0, 3.0)
    private val route =
        Route(
            id = "route-1",
            name = "R",
            waypoints =
                listOf(
                    Waypoint("w1", start, 0),
                    Waypoint("w2", end, 1),
                ),
        )

    @Before
    fun setup() {
        every { routeRepository.getRouteWithWaypoints("route-1") } returns flowOf(route)
        coEvery { settingsRepository.activateSessionSpeed(null) } returns 1.4
        coEvery { settingsRepository.activateSessionSpeed(any()) } returns 1.4
        every { settingsRepository.getHideTeleportFeatures() } returns flowOf(false)
        useCase =
            StartRouteReplayUseCase(
                context,
                settingsRepository,
                locationRepository,
                routeRepository,
                teleportUseCase,
            )
    }

    @After
    fun tearDown() {
        unmockkObject(MockLocationIntentBuilder)
    }

    private fun captureConfig(): io.mockk.CapturingSlot<RouteStartConfig> {
        mockkObject(MockLocationIntentBuilder)
        val slot = slot<RouteStartConfig>()
        every { MockLocationIntentBuilder.startRouteReplay(any(), any(), any(), capture(slot), any()) } returns mockk(relaxed = true)
        return slot
    }

    @Test
    fun execute_plantedGuidedRoute_derivesPlantingAndFollowRoads() =
        runTest {
            every { routeRepository.getRouteWithWaypoints("route-1") } returns
                flowOf(route.copy(routeType = RouteType.GUIDED, plantingRadiusMeters = 50.0))
            val config = captureConfig()

            useCase.execute("route-1")

            assertEquals(true, config.captured.isPlanting)
            assertEquals(true, config.captured.followRoadsToStart)
            assertEquals(false, config.captured.teleportBetweenWaypoints)
        }

    @Test
    fun execute_plantedTeleportRoute_hopsWithFirstWaypointWait() =
        runTest {
            every { routeRepository.getRouteWithWaypoints("route-1") } returns
                flowOf(
                    route.copy(
                        routeType = RouteType.TELEPORT,
                        plantingRadiusMeters = 50.0,
                        waypoints = route.waypoints.map { it.copy(waitSeconds = 12) },
                    ),
                )
            val config = captureConfig()

            useCase.execute("route-1")

            assertEquals(true, config.captured.isPlanting)
            assertEquals(true, config.captured.teleportBetweenWaypoints)
            assertEquals(true, config.captured.teleportToStart)
            assertEquals(12, config.captured.teleportBetweenDelaySeconds)
            coVerify { teleportUseCase.execute(start, resetMovement = false) }
        }

    @Test
    fun execute_plantedTeleportRoute_hideTeleport_walksConnectors() =
        runTest {
            every { settingsRepository.getHideTeleportFeatures() } returns flowOf(true)
            every { routeRepository.getRouteWithWaypoints("route-1") } returns
                flowOf(route.copy(routeType = RouteType.TELEPORT, plantingRadiusMeters = 50.0))
            val config = captureConfig()

            useCase.execute("route-1")

            assertEquals(false, config.captured.teleportBetweenWaypoints)
            assertEquals(true, config.captured.isPlanting)
            coVerify(exactly = 0) { teleportUseCase.execute(any(), any()) }
        }

    @Test
    fun execute_unplantedTeleportRoute_hideTeleport_walks() =
        runTest {
            every { settingsRepository.getHideTeleportFeatures() } returns flowOf(true)
            every { routeRepository.getRouteWithWaypoints("route-1") } returns flowOf(route.copy(routeType = RouteType.TELEPORT))
            val config = captureConfig()

            useCase.execute("route-1")

            assertEquals(false, config.captured.teleportBetweenWaypoints)
            assertEquals(false, config.captured.teleportToStart)
            coVerify(exactly = 0) { teleportUseCase.execute(any(), any()) }
        }

    @Test
    fun execute_unplantedTeleportRoute_bypassHideTeleport_hops() =
        runTest {
            every { settingsRepository.getHideTeleportFeatures() } returns flowOf(true)
            every { routeRepository.getRouteWithWaypoints("route-1") } returns flowOf(route.copy(routeType = RouteType.TELEPORT))
            val config = captureConfig()

            useCase.execute("route-1", bypassHideTeleport = true)

            assertEquals(true, config.captured.teleportBetweenWaypoints)
            assertEquals(true, config.captured.teleportToStart)
            coVerify(exactly = 0) { teleportUseCase.execute(any(), any()) }
        }

    @Test
    fun execute_unplantedTeleportRoute_hopsWhenVisible() =
        runTest {
            every { routeRepository.getRouteWithWaypoints("route-1") } returns flowOf(route.copy(routeType = RouteType.TELEPORT))
            val config = captureConfig()

            useCase.execute("route-1")

            assertEquals(true, config.captured.teleportBetweenWaypoints)
            assertEquals(true, config.captured.teleportToStart)
            coVerify(exactly = 0) { teleportUseCase.execute(any(), any()) }
        }

    @Test
    fun execute_unplantedGuidedRoute_leavesConfigUntouched() =
        runTest {
            every { routeRepository.getRouteWithWaypoints("route-1") } returns
                flowOf(route.copy(routeType = RouteType.GUIDED))
            val config = captureConfig()

            useCase.execute("route-1")

            assertEquals(false, config.captured.isPlanting)
            assertEquals(false, config.captured.followRoadsToStart)
        }

    @Test
    fun execute_walksToFirstWaypoint_byDefault() =
        runTest {
            useCase.execute("route-1")

            coVerify(exactly = 0) { teleportUseCase.execute(any(), any()) }
            verify { context.startService(any()) }
        }

    @Test
    fun execute_planting_stillWalksToSavedStart() =
        runTest {
            useCase.execute("route-1", RouteStartConfig(isPlanting = true))

            coVerify(exactly = 0) { teleportUseCase.execute(any(), any()) }
            verify { context.startService(any()) }
        }

    @Test
    fun execute_teleportBetweenWaypoints_teleportsToFirstWaypoint() =
        runTest {
            useCase.execute("route-1", RouteStartConfig(teleportBetweenWaypoints = true))

            coVerify { teleportUseCase.execute(start, resetMovement = false) }
            verify { context.startService(any()) }
        }

    @Test
    fun execute_teleportBetweenWaypoints_reverse_teleportsToLastWaypoint() =
        runTest {
            useCase.execute("route-1", RouteStartConfig(isReverse = true, teleportBetweenWaypoints = true))

            coVerify { teleportUseCase.execute(end, resetMovement = false) }
        }

    @Test
    fun execute_hideTeleportFeatures_ignoresTeleportBetweenWaypoints() =
        runTest {
            every { settingsRepository.getHideTeleportFeatures() } returns flowOf(true)

            useCase.execute("route-1", RouteStartConfig(teleportBetweenWaypoints = true))

            coVerify(exactly = 0) { teleportUseCase.execute(any(), any()) }
            verify { context.startService(any()) }
        }

    @Test
    fun execute_bypassHideTeleport_stillTeleportsBetweenWaypoints() =
        runTest {
            every { settingsRepository.getHideTeleportFeatures() } returns flowOf(true)

            useCase.execute("route-1", RouteStartConfig(teleportBetweenWaypoints = true), bypassHideTeleport = true)

            coVerify { teleportUseCase.execute(start, resetMovement = false) }
        }

    @Test
    fun execute_pinnedSpeed_activatesThatProfile() =
        runTest {
            every { routeRepository.getRouteWithWaypoints("route-1") } returns
                flowOf(route.copy(speedProfileId = "bike"))
            coEvery { settingsRepository.activateSessionSpeed("bike") } returns 5.0

            useCase.execute("route-1")

            coVerify { settingsRepository.activateSessionSpeed("bike") }
            verify { context.startService(any()) }
        }

    @Test
    fun routeStartConfig_defaultDelay_matchesAppConstant() {
        assertEquals(
            AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS,
            RouteStartConfig().teleportBetweenDelaySeconds,
        )
    }
}
