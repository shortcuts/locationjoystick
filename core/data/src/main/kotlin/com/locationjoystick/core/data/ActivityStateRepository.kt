package com.locationjoystick.core.data

import com.locationjoystick.core.model.MockMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified activity state combining pause conditions from all movement modes.
 *
 * This repository computes whether any movement mode is currently paused,
 * across walk-to, route replay, and roaming modes.
 * It is the single source of truth for the unified pause state.
 *
 * Callers should prefer [isActivityPaused] over manually combining individual
 * flows from [LocationRepository] and [RoamingRepository].
 */
@Singleton
class ActivityStateRepository
    @Inject
    constructor(
        private val locationRepository: LocationRepository,
        private val roamingRepository: RoamingRepository,
    ) {
        /**
         * True when the current movement mode is paused (any mode).
         * Combines walk-to, route replay, and roaming pause state.
         * Single source of truth — prefer this over combining individual flows.
         *
         * Covers all three pause cases:
         * - Walk-to paused: [LocationRepository.isWalkPaused] when in [MockMode.WALK_TO]
         * - Route replay paused: [LocationRepository.mockLocationState] == PAUSED when in [MockMode.ROUTE_REPLAY]
         * - Roaming paused: [RoamingRepository.isRoamingPaused] when in [MockMode.ROAMING]
         */
        val isActivityPaused: Flow<Boolean> =
            combine(
                locationRepository.isCurrentActivityPaused,
                locationRepository.currentMode,
                roamingRepository.isRoamingPaused,
            ) { locPaused, mode, roamingPaused ->
                locPaused || (mode == MockMode.ROAMING && roamingPaused)
            }
    }
