package com.locationjoystick.core.model

/** How a tap on the Tap to Walk overlay moves the mock position. */
enum class TapToWalkMove {
    WALK,
    VIA_ROADS,
    TELEPORT,
    ;

    companion object {
        val DEFAULT: Set<TapToWalkMove> = setOf(WALK)
    }
}

/** Returns the set with [move] added or removed, or the receiver when that would leave it empty. */
fun Set<TapToWalkMove>.withMove(
    move: TapToWalkMove,
    checked: Boolean,
): Set<TapToWalkMove> {
    val next = if (checked) this + move else this - move
    return if (next.isEmpty()) this else next
}

/**
 * The moves a tap offers. Hide teleport removes Teleport without touching the stored choice; a
 * stored choice of only Teleport then falls back to Walk so a tap never does nothing.
 */
fun effectiveTapToWalkMoves(
    stored: Set<TapToWalkMove>,
    hideTeleport: Boolean,
): List<TapToWalkMove> =
    TapToWalkMove.entries
        .filter { it in stored && !(hideTeleport && it == TapToWalkMove.TELEPORT) }
        .ifEmpty { listOf(TapToWalkMove.WALK) }
