package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.advancePosition
import com.locationjoystick.core.common.util.calculateBearing
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WalkToEngine"

/**
 * Scope-agnostic tick-loop engine for walking toward a target position.
 *
 * Used by [WalkCoordinator] (ViewModel scope) and by [FloatingWidgetService]
 * (service scope). No lifecycle coupling of its own.
 *
 * Reads the active speed profile on each tick so speed changes take effect
 * immediately — fixes the hard-coded `1.39 m/s` in the old `MapViewModel.walkTo()`.
 */
@Singleton
class WalkToEngine
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val locationRepository: LocationRepository,
    ) {
        /**
         * Launches a walk-to coroutine on the receiver [CoroutineScope].
         *
         * @param target Destination position.
         * @param onPositionUpdate Suspend callback for each new interpolated position.
         *   The caller is responsible for forwarding the update to [MockLocationService].
         * @param onArrival Called once when the walk arrives within
         *   [AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS] of [target].
         */
        fun CoroutineScope.launchWalkTo(
            target: LatLng,
            onPositionUpdate: suspend (LatLng, Float, Float) -> Unit,
            onArrival: suspend () -> Unit,
        ): Job = launchWalkAlongRoute(listOf(target), onPositionUpdate, onArrival)

        /**
         * @param onFinished Optional owner-managed cleanup on completion or cancellation.
         *   When supplied, replaces the default target cleanup; the owner must clear only
         *   its own session, atomically with any replacement start.
         * @param speedMsOverride Fixed speed in m/s used instead of the active speed profile
         *   (calibration walks must stay at the validated walking speed).
         */
        fun CoroutineScope.launchWalkAlongRoute(
            waypoints: List<LatLng>,
            onPositionUpdate: suspend (LatLng, Float, Float) -> Unit,
            onArrival: suspend () -> Unit,
            onFinished: (() -> Unit)? = null,
            speedMsOverride: Double? = null,
        ): Job {
            require(waypoints.isNotEmpty()) { "Waypoints must not be empty" }
            val finalTarget = waypoints.last()
            return launch {
                try {
                    for (waypoint in waypoints) {
                        while (true) {
                            if (locationRepository.walkTarget.value == null) return@launch
                            if (locationRepository.isWalkPaused.value) {
                                delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                                continue
                            }
                            val current = locationRepository.currentPosition.value
                            if (current == null) {
                                Log.w(TAG, "No current position; stopping walk")
                                return@launch
                            }

                            val distanceM = haversineDistance(current, waypoint)
                            if (distanceM < AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS) break

                            val speedMs =
                                speedMsOverride ?: settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                            val tick = computeWalkTick(current, waypoint, speedMs)
                            onPositionUpdate(tick.position, tick.speedMs, tick.bearingDeg)

                            delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                        }
                    }
                    Log.d(TAG, "Reached final target; stopping walk")
                    ensureActive()
                    onArrival()
                } catch (_: CancellationException) {
                    // normal cancellation — no log needed
                } catch (e: Exception) {
                    Log.e(TAG, "Walk along route to $finalTarget interrupted", e)
                } finally {
                    // A coordinator owns session identity, so it must clear the target under
                    // the same lock as start/cancel. Target equality cannot distinguish two
                    // successive walks to the same destination. Standalone callers retain
                    // the default cleanup when no session owner is supplied.
                    if (onFinished != null) {
                        onFinished()
                    } else if (locationRepository.walkTarget.value == finalTarget) {
                        locationRepository.setWalkTarget(null)
                    }
                }
            }
        }

        /**
         * Walks from [from] straight toward [target] at a fixed [speedMs], suspending until
         * arrival within [AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS].
         *
         * Unlike [launchWalkAlongRoute], this has no dependency on
         * [LocationRepository.walkTarget] / [LocationRepository.isWalkPaused] — for callers
         * (e.g. route replay's walk-to-start-of-route phase) that own their own start/stop
         * lifecycle and just need a one-shot walk with no pause support.
         */
        suspend fun walkToOnce(
            from: LatLng,
            target: LatLng,
            speedMs: Double,
            onPositionUpdate: suspend (LatLng) -> Unit,
        ) {
            var current = from
            while (haversineDistance(current, target) >= AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS) {
                val tick = computeWalkTick(current, target, speedMs)
                current = tick.position
                onPositionUpdate(current)
                delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
            }
        }

        /** One tick of "advance toward target at speedMs, capped at one update interval's distance". */
        private fun computeWalkTick(
            current: LatLng,
            target: LatLng,
            speedMs: Double,
        ): WalkTick {
            val distanceM = haversineDistance(current, target)
            val bearing = calculateBearing(current.latitude, current.longitude, target.latitude, target.longitude)
            val advanceDistance =
                minOf(speedMs * (AppConstants.LocationConstants.UPDATE_INTERVAL_MS / 1000.0), distanceM)
            val actualSpeedMs = (advanceDistance / (AppConstants.LocationConstants.UPDATE_INTERVAL_MS / 1000.0)).toFloat()
            val (newLat, newLon) = advancePosition(current.latitude, current.longitude, bearing, advanceDistance)
            return WalkTick(LatLng(newLat, newLon), actualSpeedMs, bearing.toFloat())
        }

        private data class WalkTick(
            val position: LatLng,
            val speedMs: Float,
            val bearingDeg: Float,
        )
    }
