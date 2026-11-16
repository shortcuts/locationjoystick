package com.locationjoystick.core.model

/** What a group leader is doing, as its follower mirrors it. Null elsewhere means no activity or an old leader. */
sealed interface LeaderActivity {
    val paused: Boolean

    data class Route(
        override val paused: Boolean,
        val progress: RouteProgress?,
    ) : LeaderActivity

    data class Roaming(
        override val paused: Boolean,
    ) : LeaderActivity

    data class Walking(
        override val paused: Boolean,
        val target: LatLng?,
    ) : LeaderActivity
}

fun leaderActivityOf(
    movement: MovementState,
    progress: RouteProgress?,
    walkTarget: LatLng?,
): LeaderActivity? {
    val active =
        when (movement) {
            is MovementState.Manual, is MovementState.PausePending -> return null
            is MovementState.Running -> movement.active
            is MovementState.Paused -> movement.active
        }
    val paused = movement.isPaused
    return when (active) {
        ActiveMode.ROUTE_REPLAY -> LeaderActivity.Route(paused, progress)
        ActiveMode.ROAMING -> LeaderActivity.Roaming(paused)
        ActiveMode.WALK_TO -> LeaderActivity.Walking(paused, walkTarget)
    }
}

/** The mirror shows only while following: leaving follow mode drops it without each exit path clearing it. */
fun visibleLeaderActivity(
    activity: LeaderActivity?,
    mode: MockMode,
): LeaderActivity? = activity.takeIf { mode == MockMode.FOLLOWER }
