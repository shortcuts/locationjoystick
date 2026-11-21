package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.GroupRepository
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
import com.locationjoystick.core.model.SavedItemSortMode
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoutingErrorReporter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.yield

private val walkProfile = SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = 1.4)

internal class Harness(
    val locationRepository: LocationRepository,
    val walkCoordinator: WalkCoordinator,
    val ephemeralController: EphemeralReplayController,
    val mapController: MapController,
    val teleportUseCase: TeleportUseCase,
    val groupRepository: GroupRepository,
    val context: Context,
    val roamingRepository: RoamingRepository,
    val startRouteReplayUseCase: StartRouteReplayUseCase,
) {
    fun setRoamingActive() {
        locationRepository.setMockMode(MockMode.ROAMING)
        coEvery { roamingRepository.stopRoaming() } answers {
            locationRepository.setMockMode(MockMode.TELEPORT)
        }
    }

    // Mirrors what TeleportUseCase.execute and the service do around a jump: cancel, TELEPORT mode, new position.
    fun stubEntryPoints(routeEnd: LatLng) {
        coEvery { teleportUseCase.execute(any(), any()) } coAnswers {
            walkCoordinator.cancel()
            locationRepository.setMockMode(MockMode.TELEPORT)
            locationRepository.setPositionInternal(firstArg())
        }
        coEvery { startRouteReplayUseCase.execute(any(), any(), any()) } coAnswers {
            locationRepository.cancelWalkRoadRouteFetch()
            walkCoordinator.cancel()
            yield() // the service reports ROUTE_REPLAY after the cancel, so observers see the TELEPORT edge
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
            locationRepository.setActiveRouteId("r")
            locationRepository.setRouteWaypoints(listOf(locationRepository.currentPosition.value ?: routeEnd, routeEnd))
        }
        coEvery { roamingRepository.startRoaming(any(), any()) } coAnswers {
            yield()
            locationRepository.setMockMode(MockMode.ROAMING)
            true
        }
        coEvery { roamingRepository.stopRoaming() } answers { locationRepository.setMockMode(MockMode.TELEPORT) }
    }
}

internal fun buildHarness(
    scope: CoroutineScope,
    osrmClient: OsrmClient,
    locationRepository: LocationRepository = LocationRepository(),
): Harness {
    val settingsRepository =
        mockk<SettingsRepository>(relaxed = true) {
            every { getActiveSpeedProfile() } returns flowOf(walkProfile)
            every { getRoutesSortMode() } returns flowOf(SavedItemSortMode.NEWEST_FIRST)
            every { getFavoritesSortMode() } returns flowOf(SavedItemSortMode.NEWEST_FIRST)
            every { getHomeFavoriteId() } returns flowOf(null)
            every { getSpeedUnit() } returns flowOf(SpeedUnit.KMH)
            every { getRecentSearches() } returns flowOf(emptyList())
            every { getRoamingDefaults() } returns flowOf(RoamingDefaults())
            every { getSettingsSnapshot() } returns emptyFlow()
            every { getRememberLastLocation() } returns flowOf(false)
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
    val roamingRepository =
        mockk<RoamingRepository>(relaxed = true)
    val routeRepository = mockk<RouteRepository>(relaxed = true) { every { getRoutes() } returns emptyFlow() }
    val favoriteRepository =
        mockk<FavoriteRepository>(relaxed = true) { every { getFavorites() } returns flowOf(emptyList()) }
    val teleportUseCase =
        mockk<TeleportUseCase>(relaxed = true) { every { cooldownsFor(any()) } returns emptyFlow() }
    val startRouteReplayUseCase = mockk<StartRouteReplayUseCase>(relaxed = true)
    val groupRepository = mockk<GroupRepository>(relaxed = true) { every { leaderActivity } returns MutableStateFlow(null) }

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
            serviceStarter = mockk(relaxed = true),
            realLocationRepository =
                mockk<RealLocationRepository> {
                    every { lastKnownRealPosition() } returns null
                    every { hasFinePermission() } returns false
                },
            startRouteReplayUseCase = startRouteReplayUseCase,
            ephemeralReplayController = ephemeralController,
            osrmClient = osrmClient,
            routingErrorReporter = routingErrorReporter,
            groupRepository = groupRepository,
            appScope = scope,
        )
    return Harness(
        locationRepository,
        walkCoordinator,
        ephemeralController,
        mapController,
        teleportUseCase,
        groupRepository,
        context,
        roamingRepository,
        startRouteReplayUseCase,
    )
}
