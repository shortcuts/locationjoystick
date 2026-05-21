package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.advancePosition
import com.locationjoystick.core.common.util.calculateBearing
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        ): Job =
            launch {
                try {
                    while (true) {
                        if (locationRepository.walkTarget.value == null) break
                        if (locationRepository.isWalkPaused.value) {
                            delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                            continue
                        }
                        val current = locationRepository.currentPosition.value
                        if (current == null) {
                            Log.w(TAG, "No current position; stopping walk")
                            break
                        }

                        val distanceM = haversineDistance(current, target)
                        if (distanceM < AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS) {
                            Log.d(TAG, "Reached target; stopping walk")
                            onArrival()
                            break
                        }

                        val speedMs =
                            settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                        val bearing =
                            calculateBearing(
                                current.latitude,
                                current.longitude,
                                target.latitude,
                                target.longitude,
                            )
                        val advanceDistance =
                            minOf(
                                speedMs * (AppConstants.LocationConstants.UPDATE_INTERVAL_MS / 1000.0),
                                distanceM,
                            )
                        val actualSpeedMs = (advanceDistance / (AppConstants.LocationConstants.UPDATE_INTERVAL_MS / 1000.0)).toFloat()
                        val (newLat, newLon) =
                            advancePosition(
                                current.latitude,
                                current.longitude,
                                bearing,
                                advanceDistance,
                            )
                        onPositionUpdate(LatLng(newLat, newLon), actualSpeedMs, bearing.toFloat())

                        delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Walk to $target interrupted", e)
                } finally {
                    // Only clear walk target if this coroutine owns it.
                    if (locationRepository.walkTarget.value == target) {
                        locationRepository.setWalkTarget(null)
                    }
                }
            }
    }
