package com.locationjoystick.feature.widget.impl

import com.locationjoystick.feature.widget.impl.ReconnectRunResult.FAILED
import com.locationjoystick.feature.widget.impl.ReconnectRunResult.NOT_OFF
import com.locationjoystick.feature.widget.impl.ReconnectRunResult.RECONNECTED
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectFailureStreakTest {
    private fun run(vararg results: ReconnectRunResult): List<Boolean> {
        val streak = ReconnectFailureStreak()
        return results.map(streak::record)
    }

    @Test
    fun `notifies on third failure only`() {
        assertEquals(listOf(false, false, true, false, false), run(FAILED, FAILED, FAILED, FAILED, FAILED))
    }

    @Test
    fun `not off neither counts nor resets`() {
        assertEquals(listOf(false, false, false, true), run(FAILED, NOT_OFF, FAILED, FAILED))
    }

    @Test
    fun `reconnect re-arms`() {
        assertEquals(
            listOf(false, false, true, false, false, false, true),
            run(FAILED, FAILED, FAILED, RECONNECTED, FAILED, FAILED, FAILED),
        )
    }

    @Test
    fun `reconnect before threshold resets count`() {
        assertEquals(listOf(false, false, false, false, false), run(FAILED, FAILED, RECONNECTED, FAILED, FAILED))
    }
}
