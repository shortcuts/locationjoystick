package com.locationjoystick.core.data

import com.locationjoystick.core.model.RealPositionSnapshot
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Saves the real position before the mock provider registers, because a real fix is unreadable afterwards. */
@Singleton
class RealPositionSnapshotter
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val realLocationRepository: RealLocationRepository,
    ) {
        suspend fun capture() {
            if (!settingsRepository.getLastKnownPositionEnabled().first()) return
            val position = realLocationRepository.lastKnownRealPosition() ?: return
            settingsRepository.setRealPositionSnapshot(RealPositionSnapshot(position, System.currentTimeMillis()))
        }
    }
