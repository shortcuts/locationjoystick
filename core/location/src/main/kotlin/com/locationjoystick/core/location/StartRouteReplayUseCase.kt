package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.clampTeleportBetweenDelaySeconds
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.model.startWaypoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartRouteReplayUseCase
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val locationRepository: LocationRepository,
        private val routeRepository: RouteRepository,
        private val teleportUseCase: TeleportUseCase,
    ) {
        suspend fun execute(
            routeId: String,
            isLooping: Boolean = false,
            isReverse: Boolean = false,
            isReturnToLocation: Boolean = false,
            followRoadsToStart: Boolean = false,
            isPlanting: Boolean = false,
            teleportBetweenWaypoints: Boolean = false,
            teleportBetweenDelaySeconds: Int = AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS,
        ) {
            val route = routeRepository.getRouteWithWaypoints(routeId).first()
            val speedMs = settingsRepository.activateSessionSpeed(route?.speedProfileId)
            val returnPosition = if (isReturnToLocation) locationRepository.currentPosition.value else null
            val teleportToStart = !settingsRepository.getHideTeleportFeatures().first()
            val teleportBetween = teleportBetweenWaypoints && teleportToStart
            val hopDelaySeconds = clampTeleportBetweenDelaySeconds(teleportBetweenDelaySeconds)
            if (teleportToStart) {
                route?.startWaypoint(isReverse)?.let { teleportUseCase.execute(it.position, resetMovement = false) }
            }
            val intent =
                MockLocationIntentBuilder
                    .startRouteReplay(
                        context,
                        routeId,
                        speedMs,
                        isReverse,
                        followRoadsToStart,
                        teleportToStart,
                        isPlanting,
                        teleportBetween,
                        hopDelaySeconds,
                    ).apply {
                        putExtra(MockLocationService.EXTRA_IS_LOOPING, isLooping)
                        if (returnPosition != null) {
                            putExtra(MockLocationService.EXTRA_RETURN_LAT, returnPosition.latitude)
                            putExtra(MockLocationService.EXTRA_RETURN_LON, returnPosition.longitude)
                        }
                    }
            context.startService(intent)
        }
    }
