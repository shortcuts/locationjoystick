package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.model.ReconnectRunResult
import com.locationjoystick.core.model.ReconnectRunResult.FAILED
import com.locationjoystick.core.model.ReconnectRunResult.GATE_CLOSED
import com.locationjoystick.core.model.ReconnectRunResult.NOT_FOUND
import com.locationjoystick.core.model.ReconnectRunResult.NOT_OFF
import com.locationjoystick.core.model.ReconnectRunResult.RECONNECTED
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectBackoffTest {
    private fun delays(vararg results: ReconnectRunResult): List<Long> {
        val backoff = ReconnectBackoff(baseMs = 60_000L, maxMs = 300_000L)
        return results.map {
            backoff.record(it)
            backoff.delayMs
        }
    }

    @Test
    fun `failures double the delay up to the cap`() {
        assertEquals(listOf(120_000L, 240_000L, 300_000L, 300_000L), delays(FAILED, FAILED, FAILED, FAILED))
    }

    @Test
    fun `reconnected and not off reset to base`() {
        assertEquals(listOf(120_000L, 60_000L, 120_000L, 60_000L), delays(FAILED, RECONNECTED, FAILED, NOT_OFF))
    }

    @Test
    fun `not found and gate closed keep the delay`() {
        assertEquals(listOf(120_000L, 120_000L, 120_000L), delays(FAILED, NOT_FOUND, GATE_CLOSED))
    }

    @Test
    fun `equal base and max never grows`() {
        val backoff = ReconnectBackoff(10_000L, 10_000L)
        backoff.record(FAILED)
        assertEquals(10_000L, backoff.delayMs)
    }
}
