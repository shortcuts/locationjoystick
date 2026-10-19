package com.locationjoystick.feature.widget.impl

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Android rejects screenshots closer than ~333 ms (code 3), so every shot goes through one lock and a minimum gap. */
internal class ScreenshotPacer(
    private val minGapMs: Long,
    private val now: () -> Long,
) {
    private val lock = Mutex()
    private var lastDoneAt = Long.MIN_VALUE / 2

    suspend fun <T> paced(block: suspend () -> T): T =
        lock.withLock {
            val wait = minGapMs - (now() - lastDoneAt)
            if (wait > 0) delay(wait)
            try {
                block()
            } finally {
                lastDoneAt = now()
            }
        }
}
