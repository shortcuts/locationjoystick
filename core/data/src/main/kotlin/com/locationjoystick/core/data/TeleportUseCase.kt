package com.locationjoystick.core.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
    ) {
        /**
         * Fires the teleport intent, persists the new position, and records the timestamp.
         */
        suspend fun execute(position: LatLng) {
            try {
                val intent =
                    Intent().apply {
                        setClassName(context, AppConstants.ServiceConstants.MOCK_LOCATION_SERVICE_CLASS)
                        action = AppConstants.ServiceConstants.ACTION_UPDATE_POSITION
                        putExtra(AppConstants.ServiceConstants.EXTRA_LAT, position.latitude)
                        putExtra(AppConstants.ServiceConstants.EXTRA_LON, position.longitude)
                    }
                context.startService(intent)
                settingsRepository.setLastLocation(position)
                settingsRepository.setLastTeleportTime(System.currentTimeMillis())
                Log.d(TAG, "Teleport to ${position.latitude}, ${position.longitude}")
            } catch (e: Exception) {
                Log.e(TAG, "Teleport failed", e)
            }
        }

        /**
         * Returns a [Flow] of [CooldownState] for the given [target] position.
         *
         * Combines [SettingsRepository.getLastTeleportTime] and [SettingsRepository.getLastLocation]
         * so that the state updates reactively whenever either changes.
         */
        fun cooldownFor(target: LatLng): Flow<CooldownState> =
            combine(
                settingsRepository.getLastTeleportTime(),
                settingsRepository.getLastLocation(),
            ) { lastTeleportMs, lastLocation ->
                CooldownEngine.computeState(lastTeleportMs, lastLocation, target)
            }
    }
