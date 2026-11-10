package com.locationjoystick.core.data

import android.util.Log
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Resets the real GPS provider's A-GPS data before the mock provider registers, when the setting is on. */
@Singleton
class AgpsResetter
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val realLocationRepository: RealLocationRepository,
    ) {
        suspend fun resetIfEnabled() {
            if (!settingsRepository.getAgpsResetOnStartEnabled().first()) return
            Log.i(TAG, "delete_aiding_data accepted=${realLocationRepository.resetAidingData()}")
        }

        private companion object {
            const val TAG = "AgpsResetter"
        }
    }
