package com.locationjoystick.core.data

import android.content.Context
import android.content.Intent
import app.cash.turbine.test
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.routing.RouteReplayEngine
import com.locationjoystick.core.routing.TeleportRouteEngine
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeleportUseCaseTest {
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val locationRepository = LocationRepository()
    private val roamingRepository = mockk<RoamingRepository>(relaxed = true)
    private val routeReplayEngine = mockk<RouteReplayEngine>(relaxed = true)
    private val teleportRouteEngine = mockk<TeleportRouteEngine>(relaxed = true)
    private val walkCoordinator = mockk<WalkCoordinator>(relaxed = true)
    private val startedIntents = mutableListOf<Intent>()
    private val context =
        mockk<Context>(relaxed = true) {
            every { startService(any()) } answers {
                startedIntents.add(firstArg())
                mockk()
            }
            every { startForegroundService(any()) } answers {
                startedIntents.add(firstArg())
                mockk()
            }
        }
    private val useCase =
        TeleportUseCase(
            context,
            settingsRepository,
            locationRepository,
            roamingRepository,
            routeReplayEngine,
            teleportRouteEngine,
            walkCoordinator,
        )

    init {
        every { roamingRepository.isRoaming } returns MutableStateFlow(false)
    }

    @Test
    fun `cooldownFor emits Ready when no teleport has occurred`() =
        runTest {
            every { settingsRepository.getLastTeleportTime() } returns flowOf(0L)
            every { settingsRepository.getLastLocation() } returns flowOf(null)

            useCase.cooldownFor(LatLng(35.0, 139.0)).test {
                assertEquals(CooldownState.Ready, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cooldownFor emits Ready when origin equals target`() =
        runTest {
            val target = LatLng(35.0, 139.0)
            every { settingsRepository.getLastTeleportTime() } returns flowOf(System.currentTimeMillis())
            every { settingsRepository.getLastLocation() } returns flowOf(target)

            useCase.cooldownFor(target).test {
                assertEquals(CooldownState.Ready, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cooldownFor emits Cooling when teleport was recent and target is far away`() =
        runTest {
            val recentTime = System.currentTimeMillis() - 500L
            every { settingsRepository.getLastTeleportTime() } returns flowOf(recentTime)
            every { settingsRepository.getLastLocation() } returns flowOf(LatLng(0.0, 0.0))

            useCase.cooldownFor(LatLng(10.0, 10.0)).test {
                assertTrue(awaitItem() is CooldownState.Cooling)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cooldownsFor emits Ready for all favorites when no teleport occurred`() =
        runTest {
            every { settingsRepository.getLastTeleportTime() } returns flowOf(0L)
            every { settingsRepository.getLastLocation() } returns flowOf(null)

            val favorites =
                listOf(
                    FavoriteLocation("1", "Tokyo", LatLng(35.6762, 139.6503), 0L),
                    FavoriteLocation("2", "London", LatLng(51.5074, -0.1278), 0L),
                )

            useCase.cooldownsFor(flowOf(favorites)).test {
                val map = awaitItem()
                assertEquals(2, map.size)
                assertEquals(CooldownState.Ready, map["1"])
                assertEquals(CooldownState.Ready, map["2"])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cooldownsFor emits empty map for empty favorites list`() =
        runTest {
            every { settingsRepository.getLastTeleportTime() } returns flowOf(0L)
            every { settingsRepository.getLastLocation() } returns flowOf(null)

            useCase.cooldownsFor(flowOf(emptyList())).test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cooldownsFor keys map by favorite id`() =
        runTest {
            every { settingsRepository.getLastTeleportTime() } returns flowOf(System.currentTimeMillis() - 500L)
            every { settingsRepository.getLastLocation() } returns flowOf(LatLng(0.0, 0.0))

            val favorites =
                listOf(
                    FavoriteLocation("fav-near", "Near", LatLng(0.0001, 0.0001), 0L),
                    FavoriteLocation("fav-far", "Far", LatLng(45.0, 90.0), 0L),
                )

            useCase.cooldownsFor(flowOf(favorites)).test {
                val map = awaitItem()
                assertTrue(map.containsKey("fav-near"))
                assertTrue(map.containsKey("fav-far"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `execute stops a route session before jumping`() =
        runTest {
            every { roamingRepository.isRoaming } returns MutableStateFlow(false)
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
            locationRepository.setActiveRouteId("route-1")
            val target = LatLng(10.0, 20.0)

            useCase.execute(target)

            coVerify { routeReplayEngine.stop() }
            coVerify { teleportRouteEngine.stop() }
            verify { walkCoordinator.cancel() }
            assertEquals(MockMode.TELEPORT, locationRepository.currentMode.value)
            assertNull(locationRepository.activeRouteId.value)
            assertNull(locationRepository.routeProgress.value)
            coVerify(exactly = 0) { roamingRepository.stopRoaming() }
        }

    @Test
    fun `execute stops roaming before jumping`() =
        runTest {
            every { roamingRepository.isRoaming } returns MutableStateFlow(true)
            locationRepository.setMockMode(MockMode.ROAMING)
            val target = LatLng(10.0, 20.0)

            useCase.execute(target)

            coVerify { roamingRepository.stopRoaming() }
            verify { walkCoordinator.cancel() }
            coVerify(exactly = 0) { routeReplayEngine.stop() }
            coVerify(exactly = 0) { teleportRouteEngine.stop() }
        }

    @Test
    fun `execute skip movement reset when resetMovement is false`() =
        runTest {
            every { roamingRepository.isRoaming } returns MutableStateFlow(true)
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
            locationRepository.setActiveRouteId("route-1")

            useCase.execute(LatLng(10.0, 20.0), resetMovement = false)

            coVerify(exactly = 0) { roamingRepository.stopRoaming() }
            coVerify(exactly = 0) { routeReplayEngine.stop() }
            coVerify(exactly = 0) { teleportRouteEngine.stop() }
            verify(exactly = 0) { walkCoordinator.cancel() }
            assertEquals(MockMode.ROUTE_REPLAY, locationRepository.currentMode.value)
            assertEquals("route-1", locationRepository.activeRouteId.value)
        }

    @Test
    fun `execute aborts in-flight replay even when mode is not ROUTE_REPLAY`() =
        runTest {
            every { roamingRepository.isRoaming } returns MutableStateFlow(false)
            locationRepository.setMockMode(MockMode.TELEPORT)
            startedIntents.clear()

            useCase.execute(LatLng(10.0, 20.0))

            // Stub android.jar Intents do not retain action/extras; count is the signal.
            // STOP is first, UPDATE is second (see resetActiveRouteAndRoaming).
            assertEquals(2, startedIntents.size)
        }

    @Test
    fun `execute skip movement reset does not send STOP`() =
        runTest {
            every { roamingRepository.isRoaming } returns MutableStateFlow(false)
            locationRepository.setMockMode(MockMode.TELEPORT)
            startedIntents.clear()

            useCase.execute(LatLng(10.0, 20.0), resetMovement = false)

            assertEquals(1, startedIntents.size)
        }
}
