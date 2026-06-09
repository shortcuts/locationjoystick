package com.locationjoystick.core.data

import app.cash.turbine.test
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeleportUseCaseTest {
    private val settingsRepository = mockk<SettingsRepository>()
    private val useCase = TeleportUseCase(mockk(relaxed = true), settingsRepository)

    @Test
    fun `cooldownFor emits Ready when no teleport has occurred`() = runTest {
        every { settingsRepository.getLastTeleportTime() } returns flowOf(0L)
        every { settingsRepository.getLastLocation() } returns flowOf(null)

        useCase.cooldownFor(LatLng(35.0, 139.0)).test {
            assertEquals(CooldownState.Ready, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cooldownFor emits Ready when origin equals target`() = runTest {
        val target = LatLng(35.0, 139.0)
        every { settingsRepository.getLastTeleportTime() } returns flowOf(System.currentTimeMillis())
        every { settingsRepository.getLastLocation() } returns flowOf(target)

        useCase.cooldownFor(target).test {
            assertEquals(CooldownState.Ready, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cooldownFor emits Cooling when teleport was recent and target is far away`() = runTest {
        val recentTime = System.currentTimeMillis() - 500L
        every { settingsRepository.getLastTeleportTime() } returns flowOf(recentTime)
        every { settingsRepository.getLastLocation() } returns flowOf(LatLng(0.0, 0.0))

        useCase.cooldownFor(LatLng(10.0, 10.0)).test {
            assertTrue(awaitItem() is CooldownState.Cooling)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cooldownsFor emits Ready for all favorites when no teleport occurred`() = runTest {
        every { settingsRepository.getLastTeleportTime() } returns flowOf(0L)
        every { settingsRepository.getLastLocation() } returns flowOf(null)

        val favorites = listOf(
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
    fun `cooldownsFor emits empty map for empty favorites list`() = runTest {
        every { settingsRepository.getLastTeleportTime() } returns flowOf(0L)
        every { settingsRepository.getLastLocation() } returns flowOf(null)

        useCase.cooldownsFor(flowOf(emptyList())).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cooldownsFor keys map by favorite id`() = runTest {
        every { settingsRepository.getLastTeleportTime() } returns flowOf(System.currentTimeMillis() - 500L)
        every { settingsRepository.getLastLocation() } returns flowOf(LatLng(0.0, 0.0))

        val favorites = listOf(
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
}
