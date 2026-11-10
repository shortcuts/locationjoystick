package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng

/** Everything a replay needs to continue from where it stopped. */
internal data class ReplaySnapshot(
    val position: LatLng? = null,
    val waypointIndex: Int = 1,
    val waypoints: List<LatLng> = emptyList(),
    val speedMs: Double = 0.0,
    val legReversed: Boolean = false,
    val lingerBeforeNextHop: Boolean = false,
)

/** Lifecycle of [RouteReplayEngine]. Held in one atomic reference, so each snapshot update is atomic. */
internal sealed interface ReplayState {
    val snapshot: ReplaySnapshot

    data object Idle : ReplayState {
        override val snapshot = ReplaySnapshot()
    }

    data class Playing(
        override val snapshot: ReplaySnapshot,
    ) : ReplayState

    data class Paused(
        override val snapshot: ReplaySnapshot,
    ) : ReplayState
}

internal val ReplayState.isPlaying: Boolean get() = this is ReplayState.Playing

/** Each transition returns null when it is illegal from the current state. */
internal fun ReplayState.paused(): ReplayState? = (this as? ReplayState.Playing)?.let { ReplayState.Paused(it.snapshot) }

internal fun ReplayState.resumed(from: LatLng?): ReplayState? =
    when (this) {
        ReplayState.Idle -> null
        is ReplayState.Playing -> ReplayState.Playing(snapshot.copy(position = from ?: snapshot.position))
        is ReplayState.Paused -> ReplayState.Playing(snapshot.copy(position = from ?: snapshot.position))
    }

/** A tick only counts while playing, so a tick in flight when pause lands cannot overwrite the saved position. */
internal fun ReplayState.progressed(
    position: LatLng,
    waypointIndex: Int,
): ReplayState? =
    (this as? ReplayState.Playing)?.let {
        ReplayState.Playing(it.snapshot.copy(position = position, waypointIndex = waypointIndex))
    }

/** Edits the snapshot in place in Playing or Paused. */
internal fun ReplayState.edited(edit: (ReplaySnapshot) -> ReplaySnapshot): ReplayState? =
    when (this) {
        ReplayState.Idle -> null
        is ReplayState.Playing -> copy(snapshot = edit(snapshot))
        is ReplayState.Paused -> copy(snapshot = edit(snapshot))
    }
