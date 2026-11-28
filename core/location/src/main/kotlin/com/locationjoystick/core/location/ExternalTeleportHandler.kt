package com.locationjoystick.core.location

import android.content.Context
import android.util.Log
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.MockLocationServiceStarter
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ExternalTeleportHandler"

/** Runs one outside teleport command: decide, then teleport or start spoofing at the point. */
@Singleton
class ExternalTeleportHandler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val teleportUseCase: TeleportUseCase,
        private val locationRepository: LocationRepository,
        private val settingsRepository: SettingsRepository,
        private val serviceStarter: MockLocationServiceStarter,
    ) {
        suspend fun handle(
            lat: Double?,
            lng: Double?,
        ) {
            val decision =
                externalTeleportDecision(
                    locationRepository.mockLocationState.value,
                    settingsRepository.getHideTeleportFeatures().first(),
                    lat,
                    lng,
                )
            when (decision) {
                is ExternalTeleportDecision.Teleport -> teleportUseCase.execute(decision.point)
                is ExternalTeleportDecision.StartAt -> {
                    serviceStarter.start(
                        MockLocationIntentBuilder.startSpoofing(context, decision.point.latitude, decision.point.longitude),
                    )
                }
                is ExternalTeleportDecision.Ignore -> Log.w(TAG, "ignored: ${decision.reason}")
            }
        }
    }
