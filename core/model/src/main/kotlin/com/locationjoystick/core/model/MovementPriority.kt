package com.locationjoystick.core.model

/**
 * Precedence when movement engines overlap:
 * 1. A **playing** route (`ROUTE_REPLAY` + `RUNNING`) owns the tick. Joystick is ignored
 *    and starting roam is a no-op.
 * 2. A **paused** route yields the tick to the joystick (mode stays `ROUTE_REPLAY`) and allows
 *    roam start. Starting roam must stop the
 *    paused replay first so two engines never write position together.
 * 3. **Playing** roam owns the tick over joystick. **Paused** roam yields to the joystick
 *    (mode stays `ROAMING`). Roam pause lives in [MovementState], not [MockLocationState.PAUSED].
 * 4. Walk-to yields while its own pause flag is set. Follower always owns the tick.
 *
 * Those rules only gate the joystick's own ticks (e.g. a retained locked-stick direction). A new
 * touch that moves the stick past the dead zone pauses automatic movement before steering.
 */
fun isRoutePlaying(
    mode: MockMode,
    state: MockLocationState,
): Boolean = mode == MockMode.ROUTE_REPLAY && state == MockLocationState.RUNNING

fun canStartRoaming(
    mode: MockMode,
    state: MockLocationState,
): Boolean = !isRoutePlaying(mode, state)

fun shouldIgnoreJoystickInput(movement: MovementState): Boolean =
    when (movement) {
        is MovementState.Manual -> movement.mode == MockMode.FOLLOWER
        is MovementState.Active -> !movement.paused
    }

// Modes where another engine owns position updates for its own tick — a joystick drag must not
// steal mode or overwrite position while one of these is active.
private val ENGINE_OWNED_MODES = setOf(MockMode.ROUTE_REPLAY, MockMode.ROAMING, MockMode.WALK_TO, MockMode.FOLLOWER)

/** True when releasing the stick must not reset [MockMode] — the engine still owns the session. */
fun shouldPreserveEngineMode(mode: MockMode): Boolean = mode in ENGINE_OWNED_MODES
