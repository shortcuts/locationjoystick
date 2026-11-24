package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.model.MockMode

/** Route icon (green, pause/stop popup) is for route replay or walk-to, never roaming; roaming has its own icon. */
internal fun routeControlsActive(mode: MockMode): Boolean = mode == MockMode.ROUTE_REPLAY || mode == MockMode.WALK_TO

/** The stick is actively moving (an unlocked release or a stop resets the mode to TELEPORT). */
internal fun joystickMoving(mode: MockMode): Boolean = mode == MockMode.JOYSTICK
