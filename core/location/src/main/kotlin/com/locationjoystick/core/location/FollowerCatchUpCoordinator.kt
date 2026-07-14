package com.locationjoystick.core.location

import com.locationjoystick.core.model.LatLng
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the FOLLOWER-mode catch-up target extracted from [MockLocationService], mirroring the
 * `WalkCoordinator` pattern (`:core:data`) used for walk-to: state ownership + per-tick step
 * logic live in one small class instead of scattered @Volatile fields on the service.
 */
internal class FollowerCatchUpCoordinator {
    private val target = AtomicReference<LatLng?>(null)

    /** Latest position received from the leader; walked toward per-tick, never snapped to directly. */
    fun setTarget(position: LatLng) {
        target.set(position)
    }

    fun clear() {
        target.set(null)
    }

    /** Last-known leader position, or null if no position has been received (or FOLLOWER mode is inactive). */
    fun currentTarget(): LatLng? = target.get()

    /**
     * One [computeFollowerCatchUp] step from [current] toward the tracked target at
     * [activeProfileSpeedMs]. Returns null if there is no target to walk toward.
     */
    fun advance(
        current: LatLng,
        activeProfileSpeedMs: Double,
    ): FollowerCatchUpResult? {
        val t = target.get() ?: return null
        return computeFollowerCatchUp(current, t, activeProfileSpeedMs)
    }
}
