package com.locationjoystick.core.routing

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-scoped channel for user-visible routing failures. Replaces the previous
 * per-controller `_routingErrors` flow on `MapController` so other routing-adjacent classes
 * (e.g. [EphemeralReplayController][com.locationjoystick.core.location.EphemeralReplayController],
 * [RoamingEngine]) can report without depending on `MapController`.
 */
@Singleton
class RoutingErrorReporter
    @Inject
    constructor() {
        private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val errors: SharedFlow<String> = _errors.asSharedFlow()

        fun report(message: String) {
            _errors.tryEmit(message)
        }

        /**
         * Reports one summary message when 1+ of [totalLegs] road-following legs fell back to a
         * straight line — the exact "count fallbacks, report one summary" shape duplicated between
         * RoamingEngine's dynamic road-following planner and ReplayOrchestrator's fixed-waypoint-list
         * road-following expansion before this method existed. A no-op when [fallbackCount] is 0.
         */
        fun reportRoadFollowingFallbacks(
            fallbackCount: Int,
            totalLegs: Int,
        ) {
            if (fallbackCount > 0) {
                report(
                    "Road-following partially unavailable — $fallbackCount of $totalLegs legs used straight-line paths",
                )
            }
        }
    }
