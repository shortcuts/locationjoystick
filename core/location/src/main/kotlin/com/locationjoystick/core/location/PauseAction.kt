package com.locationjoystick.core.location

import com.locationjoystick.core.data.RoadRouteFetch
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.MovementState
import com.locationjoystick.core.model.isActive
import com.locationjoystick.core.model.isPaused

enum class PauseAction { None, LeaveFollower, PauseWalk, PauseRoaming, PauseReplay }

/** What a joystick takeover pauses. Walk outranks roaming, and a road fetch can run before its mode is set. */
fun pauseActionFor(
    movement: MovementState,
    fetch: RoadRouteFetch,
): PauseAction =
    when {
        movement is MovementState.Manual && movement.mode == MockMode.FOLLOWER -> PauseAction.LeaveFollower
        movement.isActive(MockMode.WALK_TO) || fetch is RoadRouteFetch.WalkFetch ->
            if (movement.isPaused(MockMode.WALK_TO)) PauseAction.None else PauseAction.PauseWalk
        movement.isActive(MockMode.ROAMING) ->
            if (movement.isPaused(MockMode.ROAMING)) PauseAction.None else PauseAction.PauseRoaming
        movement.isActive(MockMode.ROUTE_REPLAY) || fetch is RoadRouteFetch.ReplayFetch ->
            if (movement.isPaused(MockMode.ROUTE_REPLAY)) PauseAction.None else PauseAction.PauseReplay
        else -> PauseAction.None
    }
