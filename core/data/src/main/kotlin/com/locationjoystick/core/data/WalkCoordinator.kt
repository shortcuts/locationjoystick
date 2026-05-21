package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WalkCoordinator"

/**
 * Thin facade over [WalkToEngine] for use inside a ViewModel or Service.
 *
 * Handles:
 * - Cancelling any in-flight walk before starting a new one.
 * - Forwarding position ticks to [LocationRepository].
 * - Clearing [LocationRepository.walkTarget] on arrival / cancellation.
 * - Invoking an optional [onPositionUpdate] callback (e.g. to send a service intent).
 *
 * The caller provides a [CoroutineScope] (typically `viewModelScope` or a
 * service scope) and never manages the [Job] directly.
 *
 * Usage:
 * ```kotlin
 * walkCoordinator.startWalk(target, viewModelScope) { newPos ->
 *     context.startService(MockLocationIntentBuilder.updatePosition(context, newPos.latitude, newPos.longitude))
 * }
 * walkCoordinator.cancel()
 * ```
 */
@Singleton
class WalkCoordinator
    @Inject
    constructor(
        private val locationRepository: LocationRepository,
        private val walkToEngine: WalkToEngine,
    ) {
        private var activeWalkJob: Job? = null

        /**
         * Starts a walk toward [target] on the given [scope].
         *
         * Any previously active walk is cancelled first.
         *
         * @param onPositionUpdate Optional extra callback invoked on each position tick.
         *   Use this to forward updates to a background service (e.g. via Intent).
         */
        fun startWalk(
            target: LatLng,
            scope: CoroutineScope,
            onPositionUpdate: (suspend (LatLng, Float, Float) -> Unit)? = null,
        ) {
            activeWalkJob?.cancel()
            locationRepository.setWalkTarget(target)
            locationRepository.setMockMode(MockMode.WALK_TO)

            with(walkToEngine) {
                activeWalkJob =
                    scope.launchWalkTo(
                        target = target,
                        onPositionUpdate = { newPos, speedMs, bearing ->
                            locationRepository.updatePosition(newPos)
                            onPositionUpdate?.invoke(newPos, speedMs, bearing)
                        },
                        onArrival = {
                            Log.d(TAG, "Arrived at $target")
                            locationRepository.setMockMode(MockMode.TELEPORT)
                            locationRepository.setWalkTarget(null)
                        },
                    )
            }
        }

        /** Cancels any active walk and clears the walk target. */
        fun cancel() {
            activeWalkJob?.cancel()
            activeWalkJob = null
            locationRepository.setWalkTarget(null)
        }
    }
