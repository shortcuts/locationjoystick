package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.Waypoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
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

    @Test
    fun execute_teleportsToFirstWaypoint_byDefault() =
        runTest {
            useCase.execute("route-1")

            coVerify { teleportUseCase.execute(start, resetMovement = false) }
            verify { context.startService(any()) }
        }

    @Test
    fun execute_reverse_teleportsToLastWaypoint() =
        runTest {
            useCase.execute("route-1", isReverse = true)

            coVerify { teleportUseCase.execute(end, resetMovement = false) }
        }

    @Test
    fun execute_hideTeleportFeatures_skipsTeleport() =
        runTest {
            every { settingsRepository.getHideTeleportFeatures() } returns flowOf(true)

            useCase.execute("route-1")

            coVerify(exactly = 0) { teleportUseCase.execute(any()) }
            verify { context.startService(any()) }
        }

    @Test
    fun execute_planting_stillTeleportsToSavedStart() =
        runTest {
            useCase.execute("route-1", isPlanting = true)

            coVerify { teleportUseCase.execute(start, resetMovement = false) }
            verify { context.startService(any()) }
        }

    @Test
    fun execute_hideTeleportFeatures_ignoresTeleportBetweenWaypoints() =
        runTest {
            every { settingsRepository.getHideTeleportFeatures() } returns flowOf(true)

            useCase.execute("route-1", teleportBetweenWaypoints = true)

            coVerify(exactly = 0) { teleportUseCase.execute(any()) }
            verify { context.startService(any()) }
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
}
