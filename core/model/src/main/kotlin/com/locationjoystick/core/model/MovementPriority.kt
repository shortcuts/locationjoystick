package com.locationjoystick.core.model

/**
 * Precedence when movement engines overlap:
 * 1. A **playing** route (`ROUTE_REPLAY` + `RUNNING`) owns the tick. Joystick is ignored
 *    and starting roam is a no-op.
 * 2. A **paused** route yields the tick to the joystick (mode stays `ROUTE_REPLAY` so resume
 *    still snaps to the next named stop) and allows roam start. Starting roam must stop the
 *    paused replay first so two engines never write position together.
 * 3. **Playing** roam owns the tick over joystick. **Paused** roam yields to the joystick
 *    (mode stays `ROAMING`). Roam pause is [isRoamingPaused], not [MockLocationState.PAUSED].
 * 4. Walk-to and follower own the tick over joystick even when paused.
 */
fun isRoutePlaying(
    mode: MockMode,
    state: MockLocationState,
): Boolean = mode == MockMode.ROUTE_REPLAY && state == MockLocationState.RUNNING

fun canStartRoaming(
    mode: MockMode,
    state: MockLocationState,
): Boolean = !isRoutePlaying(mode, state)

fun shouldIgnoreJoystickInput(
    mode: MockMode,
    state: MockLocationState,
    isRoamingPaused: Boolean = false,
): Boolean =
    when (mode) {
        MockMode.JOYSTICK, MockMode.TELEPORT -> false
        MockMode.ROUTE_REPLAY -> state != MockLocationState.PAUSED
        MockMode.ROAMING -> !isRoamingPaused
        MockMode.WALK_TO, MockMode.FOLLOWER -> true
    }
