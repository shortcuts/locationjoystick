package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.RoamingKind
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoutingErrorReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapControllerRoamingSpeedTest {
    private val walkProfile = SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = 1.4)
    private val here = LatLng(48.8566, 2.3522)

    @Test
    fun `startRoaming planting activates planting speed not walk-around speed`() =
        runTest(UnconfinedTestDispatcher()) {
            val roamingRepository = mockk<RoamingRepository>(relaxed = true)
            val settingsRepository = settingsMock()
            coEvery { settingsRepository.activateSessionSpeed("bike") } returns 5.0
            coEvery { roamingRepository.startRoaming(any(), 5.0) } returns true
            every { roamingRepository.isRoaming } returns MutableStateFlow(false)
            every { roamingRepository.isRoamingPaused } returns MutableStateFlow(false)

            val controller = controller(settingsRepository, roamingRepository, backgroundScope)
            controller.startRoaming(
                RoamingDefaults(
                    kind = RoamingKind.PLANTING,
                    speedProfileId = "walk",
                    plantingSpeedProfileId = "bike",
                ),
                here,
            )

            coVerify { settingsRepository.activateSessionSpeed("bike") }
            coVerify { roamingRepository.startRoaming(any(), 5.0) }
        }

    @Test
    fun `startRoaming walk-around activates walk-around speed not planting speed`() =
        runTest(UnconfinedTestDispatcher()) {
            val roamingRepository = mockk<RoamingRepository>(relaxed = true)
            val settingsRepository = settingsMock()
            coEvery { settingsRepository.activateSessionSpeed("walk") } returns 1.4
            coEvery { roamingRepository.startRoaming(any(), 1.4) } returns true
            every { roamingRepository.isRoaming } returns MutableStateFlow(false)
            every { roamingRepository.isRoamingPaused } returns MutableStateFlow(false)

            val controller = controller(settingsRepository, roamingRepository, backgroundScope)
            controller.startRoaming(
                RoamingDefaults(
                    kind = RoamingKind.WALK_AROUND,
                    speedProfileId = "walk",
                    plantingSpeedProfileId = "bike",
                ),
                here,
            )

            coVerify { settingsRepository.activateSessionSpeed("walk") }
            coVerify { roamingRepository.startRoaming(any(), 1.4) }
        }

    private fun settingsMock(): SettingsRepository =
        mockk(relaxed = true) {
            every { getActiveSpeedProfile() } returns flowOf(walkProfile)
            every { getRoutesSortMode() } returns flowOf(com.locationjoystick.core.model.SavedItemSortMode.NEWEST_FIRST)
            every { getFavoritesSortMode() } returns flowOf(com.locationjoystick.core.model.SavedItemSortMode.NEWEST_FIRST)
            every { getSpeedUnit() } returns flowOf(SpeedUnit.KMH)
            every { getRecentSearches() } returns flowOf(emptyList())
            every { getRoamingDefaults() } returns flowOf(RoamingDefaults())
            every { getSettingsSnapshot() } returns emptyFlow()
            every { getRememberLastLocation() } returns flowOf(false)
            every { getDebugStatsEnabled() } returns flowOf(false)
            every { getMapFeatureOrder() } returns flowOf(emptyList())
            every { getEnabledMapFeatures() } returns flowOf(emptySet())
        }

    private fun controller(
        settingsRepository: SettingsRepository,
        roamingRepository: RoamingRepository,
        appScope: CoroutineScope,
    ): MapController {
        val locationRepository = LocationRepository()
        val ephemeral =
            mockk<EphemeralReplayController>(relaxed = true) {
                every { pendingWaypoints } returns MutableStateFlow(emptyList())
            }
        return MapController(
            context = mockk<Context>(relaxed = true),
            locationRepository = locationRepository,
            routeRepository = mockk<RouteRepository>(relaxed = true) { every { getRoutes() } returns emptyFlow() },
            favoriteRepository =
                mockk<FavoriteRepository>(relaxed = true) { every { getFavorites() } returns flowOf(emptyList()) },
            settingsRepository = settingsRepository,
            roamingRepository = roamingRepository,
            walkCoordinator = mockk<WalkCoordinator>(relaxed = true),
            teleportUseCase =
                mockk<TeleportUseCase>(relaxed = true) { every { cooldownsFor(any()) } returns emptyFlow() },
            startRouteReplayUseCase = mockk<StartRouteReplayUseCase>(relaxed = true),
            ephemeralReplayController = ephemeral,
            osrmClient = mockk<OsrmClient>(relaxed = true),
            routingErrorReporter = RoutingErrorReporter(mockk<android.content.Context>(relaxed = true)),
            appScope = appScope,
        )
    }
}
