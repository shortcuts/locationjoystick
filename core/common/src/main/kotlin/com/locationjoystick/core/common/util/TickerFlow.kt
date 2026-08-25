package com.locationjoystick.core.common.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

/** Emits [Unit] immediately and then every [intervalMs] milliseconds. Cancelled with its scope. */
fun tickerFlow(intervalMs: Long) =
    flow {
        while (true) {
            emit(Unit)
            delay(intervalMs)
        }
    }
