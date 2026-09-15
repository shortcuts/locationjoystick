package com.locationjoystick.core.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.routing.RouteReplayEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TeleportUseCase"

/**
 * Single entry point for all teleport operations.
 *
 * Responsibilities:
 * 1. Fire an [AppConstants.ServiceConstants.ACTION_UPDATE_POSITION] intent to MockLocationService.
 * 2. Persist [SettingsRepository.setLastLocation] so the last position is remembered.
 * 3. Persist [SettingsRepository.setLastTeleportTime] for cooldown tracking.
 *
 * Both [com.locationjoystick.feature.map.impl.MapViewModel] and
 * [com.locationjoystick.feature.favorites.impl.FavoritesViewModel] inject this use case so all
 * teleport paths go through the same persistence logic.
 */
@Singleton
class TeleportUseCase
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val locationRepository: LocationRepository,
        private val roamingRepository: RoamingRepository,
        private val routeReplayEngine: RouteReplayEngine,
        private val walkCoordinator: WalkCoordinator,
    ) {
        /**
         * Fires the teleport intent, persists the new position, and records the timestamp.
         *
         * When [resetMovement] is true (the default for user-initiated jumps from the map,
         * a favorite, or pasted coordinates), any walk, roam, or route session is cleared
         * first so the replay/roam engine cannot overwrite the new position on the next tick.
         * Follow-roads route starts that have not set ROUTE_REPLAY yet are aborted via
         * ACTION_ROUTE_REPLAY_STOP as well, so a late routing result cannot snap GPS back.
         * Pass false when the jump is only a prelude to starting a saved route.
         */
        suspend fun execute(
            position: LatLng,
            resetMovement: Boolean = true,
        ) {
            try {
                if (resetMovement) {
                    resetActiveRouteAndRoaming()
                }
                val intent =
                    Intent().apply {
                        setClassName(context, AppConstants.ServiceConstants.MOCK_LOCATION_SERVICE_CLASS)
                        action = AppConstants.ServiceConstants.ACTION_UPDATE_POSITION
                        putExtra(AppConstants.ServiceConstants.EXTRA_LAT, position.latitude)
                        putExtra(AppConstants.ServiceConstants.EXTRA_LON, position.longitude)
                    }
                startMockLocationService(intent)
                settingsRepository.setLastLocation(position)
                settingsRepository.setLastTeleportTime(System.currentTimeMillis())
                Log.d(TAG, "Teleport to ${position.latitude}, ${position.longitude}")
            } catch (e: Exception) {
                Log.e(TAG, "Teleport failed", e)
            }
        }

        private fun startMockLocationService(intent: Intent) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
        }

        private suspend fun resetActiveRouteAndRoaming() {
            walkCoordinator.cancel()
            if (locationRepository.currentMode.value == MockMode.ROAMING || roamingRepository.isRoaming.value) {
                roamingRepository.stopRoaming()
            }
            // Always abort replay, even when mode is not yet ROUTE_REPLAY: Follow-roads
            // planning sets that mode only after OSRM returns, and a late start would
            // overwrite this teleport. STOP is a no-op when nothing is playing.
            if (locationRepository.currentMode.value == MockMode.ROUTE_REPLAY) {
                routeReplayEngine.stop()
                locationRepository.setRouteWaypoints(null)
                locationRepository.setRouteProgress(null)
                locationRepository.setActiveRouteId(null)
            }
            locationRepository.setMockMode(MockMode.TELEPORT)
            val stopReplay =
                Intent().apply {
                    setClassName(context, AppConstants.ServiceConstants.MOCK_LOCATION_SERVICE_CLASS)
                    action = AppConstants.ServiceConstants.ACTION_ROUTE_REPLAY_STOP
                }
            startMockLocationService(stopReplay)
        }

        /**
         * Returns a [Flow] of [CooldownState] for the given [target] position.
         *
         * Ticks every second so remaining-time display stays current. Seeds both DataStore
         * flows with safe defaults via [onStart] so [combine] emits synchronously on first
         * collection — the badge appears immediately without a flicker delay.
         */
        fun cooldownFor(target: LatLng): Flow<CooldownState> {
            val ticker =
                flow {
                    while (true) {
                        emit(Unit)
                        delay(1_000L)
                    }
                }
            return combine(
                settingsRepository.getLastTeleportTime().onStart { emit(0L) },
                settingsRepository.getLastLocation().onStart { emit(null) },
                ticker,
            ) { lastTeleportMs, lastLocation, _ ->
                CooldownEngine.computeState(lastTeleportMs, lastLocation, target)
            }
        }

        /**
         * Returns a [Flow] of cooldown states keyed by favorite ID.
         *
         * Re-evaluates every 30 seconds so remaining-time displays stay current without requiring
         * external DataStore writes. The ticker is an implementation detail of this use case.
         */
        fun cooldownsFor(favoritesFlow: Flow<List<FavoriteLocation>>): Flow<Map<String, CooldownState>> {
            val ticker =
                flow {
                    while (true) {
                        emit(Unit)
                        delay(30_000L)
                    }
                }
            return combine(
                settingsRepository.getLastTeleportTime(),
                settingsRepository.getLastLocation(),
                favoritesFlow,
                ticker,
            ) { teleportTime, lastLoc, favorites, _ ->
                favorites.associate { fav ->
                    fav.id to CooldownEngine.computeState(teleportTime, lastLoc, fav.position)
                }
            }
        }
    }
