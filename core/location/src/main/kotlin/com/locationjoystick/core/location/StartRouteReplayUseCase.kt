package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.util.clampTeleportBetweenDelaySeconds
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.model.RouteStartConfig
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
            config: RouteStartConfig = RouteStartConfig(),
        ) {
            val route = routeRepository.getRouteWithWaypoints(routeId).first()
            val speedMs = settingsRepository.activateSessionSpeed(route?.speedProfileId)
            val returnPosition = if (config.isReturnToLocation) locationRepository.currentPosition.value else null
            // Walking to the first waypoint is the default (docs/features/routes.md "Start Flow").
            // Teleport between waypoints requires teleporting to the start too, since MockLocationService
            // only honors the hop mode when teleportToStart is also set.
            val hop = config.teleportBetweenWaypoints && !settingsRepository.getHideTeleportFeatures().first()
            val effective =
                config.copy(
                    teleportToStart = hop,
                    teleportBetweenWaypoints = hop,
                    teleportBetweenDelaySeconds = clampTeleportBetweenDelaySeconds(config.teleportBetweenDelaySeconds),
                )
            if (hop) {
                route?.startWaypoint(config.isReverse)?.let { teleportUseCase.execute(it.position, resetMovement = false) }
            }
            context.startService(MockLocationIntentBuilder.startRouteReplay(context, routeId, speedMs, effective, returnPosition))
        }
    }
