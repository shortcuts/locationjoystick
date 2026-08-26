package com.locationjoystick.core.location

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Outcome of [FollowerCatchUpCoordinator.handleLeaderActiveUpdate] — the compareAndSet gating
 * has already happened, so the caller can act on the result unconditionally. */
internal enum class FollowerBootstrapAction { BOOTSTRAP, PAUSE, NONE }

/**
 * Owns the FOLLOWER-mode catch-up target and the speed/bearing it produces, extracted from
 * [MockLocationService], mirroring the `WalkCoordinator` pattern (`:core:data`) used for walk-to:
 * state ownership + per-tick step logic live in one small class instead of scattered @Volatile
 * fields on the service. [MockLocationService] only reads [currentSpeedMs]/[currentBearing]
 * while in FOLLOWER mode — no need to hand-clear its own fields on exit or teleport.
 */
internal class FollowerCatchUpCoordinator {
    private val target = AtomicReference<LatLng?>(null)

    /** Tracks whether this device has already bootstrapped spoofing for the current leader-active
     * streak — gates [handleLeaderActiveUpdate] so BOOTSTRAP/PAUSE each fire exactly once per
     * streak, mirroring the AtomicBoolean previously scoped to the enterFollowerMode() call site. */
    private val spoofingStarted = AtomicBoolean(false)

    @Volatile private var speedMs: Float = 0f

    @Volatile private var bearing: Float = 0f

    @Volatile private var leaderBearing: Float = 0f

    /**
     * Latest position received from the leader; walked toward per-tick, never snapped to
     * directly. [leaderBearing] is the leader's own reported heading, used once the follower
     * catches up — see [advance].
     */
    fun setTarget(
        position: LatLng,
        leaderBearing: Float,
    ) {
        target.set(position)
        this.leaderBearing = leaderBearing
    }

    fun clear() {
        target.set(null)
        speedMs = 0f
        bearing = 0f
        leaderBearing = 0f
        spoofingStarted.set(false)
    }

    /** Last-known leader position, or null if no position has been received (or FOLLOWER mode is inactive). */
    fun currentTarget(): LatLng? = target.get()

    /** Speed produced by the last [advance] step (0 once arrived, or after [clear]/[markArrived]). */
    fun currentSpeedMs(): Float = speedMs

    /** Bearing produced by the last [advance] step that returned a non-null bearing. */
    fun currentBearing(): Float = bearing

    /** Zeroes the reported speed without clearing the target — used after a manual teleport-to-leader. */
    fun markArrived() {
        speedMs = 0f
        bearing = leaderBearing
    }

    /**
     * One [computeFollowerCatchUp] step from [current] toward the tracked target at
     * [activeProfileSpeedMs]. Updates [currentSpeedMs]/[currentBearing] and returns the new
     * position, or null if there is no target to walk toward.
     */
    fun advance(
        current: LatLng,
        activeProfileSpeedMs: Double,
    ): FollowerCatchUpResult? {
        val t = target.get() ?: return null
        val result = computeFollowerCatchUp(current, t, activeProfileSpeedMs)
        speedMs = result.speedMs
        // Null bearing means the step snapped (arrived, or overshot) — report the leader's own
        // heading instead of freezing whatever direction this follower's catch-up walk last
        // pointed, which differs per-device and has no relation to the leader's actual bearing.
        bearing = result.bearing ?: leaderBearing
        return result
    }

    /**
     * Reacts to one leader position update's `active` flag, extracted from
     * [MockLocationService.enterFollowerMode]'s onPosition callback. Wraps
     * [computeFollowerActiveAction] with the compareAndSet that gates each action to fire exactly
     * once per active/inactive streak.
     *
     * BOOTSTRAP means spoofing wasn't active yet on this device — nothing was being reported to
     * other apps, so snapping straight to the leader's position carries no anti-cheat risk. PAUSE
     * means the leader stopped spoofing while this device was mirroring it; the caller pauses
     * without tearing the service down so the next BOOTSTRAP can resume it.
     */
    fun handleLeaderActiveUpdate(
        leaderActive: Boolean,
        currentState: MockLocationState,
    ): FollowerBootstrapAction =
        when (computeFollowerActiveAction(leaderActive, spoofingStarted.get(), currentState)) {
            FollowerActiveAction.BOOTSTRAP ->
                if (spoofingStarted.compareAndSet(false, true)) FollowerBootstrapAction.BOOTSTRAP else FollowerBootstrapAction.NONE
            FollowerActiveAction.PAUSE ->
                if (spoofingStarted.compareAndSet(true, false)) FollowerBootstrapAction.PAUSE else FollowerBootstrapAction.NONE
            FollowerActiveAction.NO_OP -> FollowerBootstrapAction.NONE
        }
}
