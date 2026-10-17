package com.locationjoystick.core.model

/** Last reconnect check, held in memory only. */
data class ReconnectStatus(
    val result: ReconnectRunResult,
    val atMillis: Long,
)
