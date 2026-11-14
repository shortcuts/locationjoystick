package com.locationjoystick.core.location

import com.locationjoystick.core.data.RoadRouteFetch
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.MovementState
import com.locationjoystick.core.model.isActive
import com.locationjoystick.core.model.isPaused

enum class PauseAction { None, LeaveFollower, PauseWalk, PauseRoaming, PauseReplay }

/** What a joystick takeover pauses. Walk outranks roaming, and a replay road fetch can run before the mode is ROUTE_REPLAY. */
fun pauseActionFor(
    movement: MovementState,
    fetch: RoadRouteFetch,
    spoofing: MockLocationState,
): PauseAction =
    when (movement) {
        is MovementState.Manual ->
            when {
                movement.mode == MockMode.FOLLOWER -> PauseAction.LeaveFollower
                fetch is RoadRouteFetch.WalkFetch -> PauseAction.PauseWalk
                fetch is RoadRouteFetch.ReplayFetch -> replayPause(spoofing)
                else -> PauseAction.None
            }
        is MovementState.Active ->
            when {
                movement.isActive(MockMode.WALK_TO) || fetch is RoadRouteFetch.WalkFetch ->
                    if (movement.isPaused(MockMode.WALK_TO)) PauseAction.None else PauseAction.PauseWalk
                movement.isActive(MockMode.ROAMING) ->
                    if (movement.isPaused(MockMode.ROAMING)) PauseAction.None else PauseAction.PauseRoaming
                else -> replayPause(spoofing)
            }
    }

private fun replayPause(spoofing: MockLocationState): PauseAction =
    if (spoofing == MockLocationState.PAUSED) PauseAction.None else PauseAction.PauseReplay
