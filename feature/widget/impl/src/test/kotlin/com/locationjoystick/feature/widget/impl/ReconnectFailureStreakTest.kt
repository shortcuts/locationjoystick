package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.feature.widget.impl.ReconnectRunResult.FAILED
import com.locationjoystick.feature.widget.impl.ReconnectRunResult.NOT_FOUND
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

    @Test
    fun `not found never counts toward failures`() {
        assertEquals(List(5) { false }, run(FAILED, FAILED, NOT_FOUND, NOT_FOUND, NOT_FOUND))
    }

    @Test
    fun `not found notifies once per streak and any other result re-arms`() {
        val n = AppConstants.ReconnectTapConstants.NOT_FOUND_NOTIFY_THRESHOLD
        val streak = ReconnectNotFoundStreak()
        val first = List(n + 2) { streak.record(NOT_FOUND) }
        assertEquals(List(n - 1) { false } + true + false + false, first)
        streak.record(NOT_FOUND)
        assertEquals(false, streak.record(NOT_OFF))
        val second = List(n) { streak.record(NOT_FOUND) }
        assertEquals(List(n - 1) { false } + true, second)
    }
}
