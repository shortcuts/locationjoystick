package com.locationjoystick.core.model

/**
 * Outcome of one reconnect check. NOT_FOUND: the game was in front but the first read found no control
 * (HUD moved or colours changed), or a read returned nothing before any tap. NOT_OFF: the control read ON.
 * GATE_CLOSED: screen off, the game not in front, or every tap was undelivered.
 */
enum class ReconnectRunResult { NOT_OFF, NOT_FOUND, RECONNECTED, FAILED, GATE_CLOSED }
