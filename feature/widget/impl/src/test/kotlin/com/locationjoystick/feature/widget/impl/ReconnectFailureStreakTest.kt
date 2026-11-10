package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.ReconnectRunResult
import com.locationjoystick.core.model.ReconnectRunResult.FAILED
import com.locationjoystick.core.model.ReconnectRunResult.GATE_CLOSED
import com.locationjoystick.core.model.ReconnectRunResult.NOT_FOUND
import com.locationjoystick.core.model.ReconnectRunResult.NOT_OFF
import com.locationjoystick.core.model.ReconnectRunResult.PAUSED
import com.locationjoystick.core.model.ReconnectRunResult.RECONNECTED
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectFailureStreakTest {
    private fun run(vararg results: ReconnectRunResult): List<Boolean> {
        val streak = ReconnectFailureStreak()
        return results.map(streak::record)
    }

    @Test
    fun `notifies on second failure only`() {
        assertEquals(listOf(false, true, false, false), run(FAILED, FAILED, FAILED, FAILED))
    }

    @Test
    fun `not off re-arms`() {
        assertEquals(
            listOf(false, true, false, false, true),
            run(FAILED, FAILED, NOT_OFF, FAILED, FAILED),
        )
    }

    @Test
    fun `reset re-arms both streaks`() {
        val failures = ReconnectFailureStreak()
        repeat(3) { failures.record(FAILED) }
        failures.reset()
        assertEquals(listOf(false, true, false), List(3) { failures.record(FAILED) })
        val n = AppConstants.ReconnectTapConstants.NOT_FOUND_NOTIFY_THRESHOLD
        val misses = ReconnectNotFoundStreak()
        repeat(n) { misses.record(NOT_FOUND) }
        misses.reset()
        assertEquals(List(n - 1) { false } + true, List(n) { misses.record(NOT_FOUND) })
    }

    @Test
    fun `gate closed and paused neither count nor reset`() {
        assertEquals(listOf(false, false, true), run(FAILED, GATE_CLOSED, FAILED))
        assertEquals(listOf(false, true, false, false), run(FAILED, FAILED, PAUSED, FAILED))
    }

    @Test
    fun `reconnect re-arms`() {
        assertEquals(
            listOf(false, true, false, false, true),
            run(FAILED, FAILED, RECONNECTED, FAILED, FAILED),
        )
    }

    @Test
    fun `reconnect before threshold resets count`() {
        assertEquals(listOf(false, false, false, false), run(FAILED, RECONNECTED, FAILED, RECONNECTED))
    }

    @Test
    fun `exhausted flips on the second failure and clears on not off, reconnect and reset`() {
        val streak = ReconnectFailureStreak()
        streak.record(FAILED)
        assertEquals(false, streak.exhausted)
        streak.record(FAILED)
        assertEquals(true, streak.exhausted)
        streak.record(PAUSED)
        streak.record(NOT_FOUND)
        streak.record(GATE_CLOSED)
        assertEquals(true, streak.exhausted)
        streak.record(NOT_OFF)
        assertEquals(false, streak.exhausted)
        repeat(2) { streak.record(FAILED) }
        streak.record(RECONNECTED)
        assertEquals(false, streak.exhausted)
        repeat(2) { streak.record(FAILED) }
        streak.reset()
        assertEquals(false, streak.exhausted)
    }

    @Test
    fun `not found never counts toward failures`() {
        assertEquals(List(4) { false }, run(FAILED, NOT_FOUND, NOT_FOUND, NOT_FOUND))
    }

    @Test
    fun `not found notifies once per streak and any other result re-arms`() {
        val n = AppConstants.ReconnectTapConstants.NOT_FOUND_NOTIFY_THRESHOLD
        val streak = ReconnectNotFoundStreak()
        val first = List(n + 2) { streak.record(NOT_FOUND) }
        assertEquals(List(n - 1) { false } + true + false + false, first)
        streak.record(NOT_FOUND)
        assertEquals(false, streak.record(GATE_CLOSED))
        val second = List(n) { streak.record(NOT_FOUND) }
        assertEquals(List(n - 1) { false } + true, second)
    }

    @Test
    fun `gate closed notifies once after the threshold with the screen on`() {
        var clock = 0L
        val streak = ReconnectGateClosedStreak { clock }
        val threshold = AppConstants.ReconnectTapConstants.GATE_CLOSED_NOTIFY_MS
        assertEquals(false, streak.record(GATE_CLOSED, true))
        clock = threshold - 1
        assertEquals(false, streak.record(GATE_CLOSED, true))
        clock = threshold
        assertEquals(true, streak.record(GATE_CLOSED, true))
        clock = threshold + 1
        assertEquals(false, streak.record(GATE_CLOSED, true))
    }

    @Test
    fun `any other result re-arms the gate closed streak`() {
        var clock = 0L
        val streak = ReconnectGateClosedStreak { clock }
        val threshold = AppConstants.ReconnectTapConstants.GATE_CLOSED_NOTIFY_MS
        streak.record(GATE_CLOSED, true)
        clock = threshold
        assertEquals(true, streak.record(GATE_CLOSED, true))
        assertEquals(false, streak.record(FAILED, true))
        clock = threshold + 1
        assertEquals(false, streak.record(GATE_CLOSED, true))
        clock = threshold * 2 + 1
        assertEquals(true, streak.record(GATE_CLOSED, true))
    }

    @Test
    fun `screen off gate closed resets the gate closed streak`() {
        var clock = 0L
        val streak = ReconnectGateClosedStreak { clock }
        val threshold = AppConstants.ReconnectTapConstants.GATE_CLOSED_NOTIFY_MS
        streak.record(GATE_CLOSED, true)
        clock = threshold - 1
        assertEquals(false, streak.record(GATE_CLOSED, false))
        clock = threshold
        assertEquals(false, streak.record(GATE_CLOSED, true))
        clock = threshold * 2
        assertEquals(true, streak.record(GATE_CLOSED, true))
    }

    @Test
    fun `reset re-arms the gate closed streak`() {
        var clock = 0L
        val streak = ReconnectGateClosedStreak { clock }
        val threshold = AppConstants.ReconnectTapConstants.GATE_CLOSED_NOTIFY_MS
        streak.record(GATE_CLOSED, true)
        clock = threshold
        assertEquals(true, streak.record(GATE_CLOSED, true))
        streak.reset()
        assertEquals(false, streak.record(GATE_CLOSED, true))
        clock = threshold * 2
        assertEquals(true, streak.record(GATE_CLOSED, true))
    }
}
