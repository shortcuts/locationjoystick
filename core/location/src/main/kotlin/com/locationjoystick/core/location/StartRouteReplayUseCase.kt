package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.util.clampTeleportBetweenDelaySeconds
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.MockLocationServiceStarter
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.model.RouteStartConfig
import com.locationjoystick.core.model.RouteType
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
        private val serviceStarter: MockLocationServiceStarter,
    ) {
        suspend fun execute(
            routeId: String,
            config: RouteStartConfig = RouteStartConfig(),
            bypassHideTeleport: Boolean = false,
        ) {
            val route = routeRepository.getRouteWithWaypoints(routeId).first()
            val speedMs = settingsRepository.activateSessionSpeed(route?.speedProfileId)
            val returnPosition = if (config.isReturnToLocation) locationRepository.currentPosition.value else null
            // Walking to the first waypoint is the default (docs/features/routes.md "Start Flow").
            // Teleport between waypoints requires teleporting to the start too, since MockLocationService
            // only honors the hop mode when teleportToStart is also set.
            // A planted route's style decides travel between circles (docs/features/routes.md "Planting").
            val planted = route?.plantingRadiusMeters != null
            val plantedTeleport = planted && route.routeType == RouteType.TELEPORT
            // A saved teleport route walks when Hide Teleport is on; the orchestrator picks the engine from the hop flag.
            val unplantedTeleport = !planted && route?.routeType == RouteType.TELEPORT
            val base =
                if (!planted) {
                    config.copy(teleportBetweenWaypoints = config.teleportBetweenWaypoints || unplantedTeleport)
                } else {
                    config.copy(
                        isPlanting = true,
                        followRoadsToStart = config.followRoadsToStart || route.routeType == RouteType.GUIDED,
                        teleportBetweenWaypoints = config.teleportBetweenWaypoints || plantedTeleport,
                        teleportBetweenDelaySeconds =
                            if (plantedTeleport) {
                                route.waypoints.firstOrNull()?.waitSeconds ?: config.teleportBetweenDelaySeconds
                            } else {
                                config.teleportBetweenDelaySeconds
                            },
                    )
                }
            val hop = base.teleportBetweenWaypoints && (bypassHideTeleport || !settingsRepository.getHideTeleportFeatures().first())
            val effective =
                base.copy(
                    teleportToStart = hop,
                    teleportBetweenWaypoints = hop,
                    teleportBetweenDelaySeconds = clampTeleportBetweenDelaySeconds(base.teleportBetweenDelaySeconds),
                )
            if (hop && !unplantedTeleport) {
                route?.startWaypoint(config.isReverse)?.let { teleportUseCase.execute(it.position, resetMovement = false) }
            }
            serviceStarter.start(MockLocationIntentBuilder.startRouteReplay(context, routeId, speedMs, effective, returnPosition))
        }
    }
