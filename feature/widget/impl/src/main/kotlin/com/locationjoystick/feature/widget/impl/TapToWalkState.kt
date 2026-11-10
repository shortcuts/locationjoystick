package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.model.LatLng

/** Walking keeps its tap after the overlay closes, so a newer ground map can still re-aim the walk. */
internal sealed interface TapToWalkState {
    data object Hidden : TapToWalkState

    data class Shown(
        val overlay: TapToWalkOverlay,
    ) : TapToWalkState

    data class Walking(
        val overlay: TapToWalkOverlay?,
        val tap: GroundTap,
        val target: LatLng,
    ) : TapToWalkState
}

internal sealed interface TapToWalkEvent {
    data class OverlayOpened(
        val overlay: TapToWalkOverlay,
    ) : TapToWalkEvent

    data object OverlayClosed : TapToWalkEvent

    data class Aimed(
        val tap: GroundTap,
        val target: LatLng,
    ) : TapToWalkEvent

    data object WalkEnded : TapToWalkEvent
}

internal val TapToWalkState.overlay: TapToWalkOverlay?
    get() =
        when (this) {
            TapToWalkState.Hidden -> null
            is TapToWalkState.Shown -> overlay
            is TapToWalkState.Walking -> overlay
        }

internal val TapToWalkState.isActive: Boolean get() = overlay != null

/** Returns the next state, or null when [event] is illegal in this state. */
internal fun TapToWalkState.next(event: TapToWalkEvent): TapToWalkState? =
    when (event) {
        is TapToWalkEvent.OverlayOpened ->
            when (this) {
                TapToWalkState.Hidden -> TapToWalkState.Shown(event.overlay)
                is TapToWalkState.Walking -> if (overlay == null) copy(overlay = event.overlay) else null
                is TapToWalkState.Shown -> null
            }
        TapToWalkEvent.OverlayClosed ->
            when (this) {
                TapToWalkState.Hidden -> null
                is TapToWalkState.Shown -> TapToWalkState.Hidden
                is TapToWalkState.Walking -> copy(overlay = null)
            }
        is TapToWalkEvent.Aimed ->
            when (this) {
                TapToWalkState.Hidden -> TapToWalkState.Walking(null, event.tap, event.target)
                is TapToWalkState.Shown -> TapToWalkState.Walking(overlay, event.tap, event.target)
                is TapToWalkState.Walking -> copy(tap = event.tap, target = event.target)
            }
        TapToWalkEvent.WalkEnded ->
            when (this) {
                is TapToWalkState.Walking -> overlay?.let { TapToWalkState.Shown(it) } ?: TapToWalkState.Hidden
                else -> null
            }
    }
