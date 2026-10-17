package com.locationjoystick.core.model

/**
 * Outcome of one reconnect check. NOT_FOUND: the game was in front but the first read found no control
 * (HUD moved or colours changed). GATE_CLOSED: screen off or the game not in front.
 */
enum class ReconnectRunResult { NOT_OFF, NOT_FOUND, RECONNECTED, FAILED, GATE_CLOSED }
