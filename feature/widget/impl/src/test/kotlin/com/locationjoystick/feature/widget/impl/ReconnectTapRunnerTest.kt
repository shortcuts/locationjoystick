package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.model.ReconnectRunResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectTapRunnerTest {
    private val off = ReconnectControl(ReconnectState.OFF, 900, 200)
    private val on = ReconnectControl(ReconnectState.ON, 900, 200)

    private class Taps {
        var count = 0
        val times = mutableListOf<Long>()
    }

    private fun reads(vararg items: ReconnectControl?): suspend () -> ReconnectControl? {
        val queue = ArrayDeque(items.toList())
        // Past the script the last item repeats.
        return { if (queue.size > 1) queue.removeFirst() else queue.first() }
    }

    @Test
    fun `off twice then on after one tap reconnects with one tap`() =
        runTest {
            val taps = Taps()
            val result =
                runReconnectTap(1080, 2400, reads(off, off, off, on), { _, _, _ ->
                    taps.count++
                    true
                })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(1, taps.count)
        }

    @Test
    fun `on null or flapping first reads never tap`() =
        runTest {
            for (script in listOf(listOf(on), listOf(off, on))) {
                val taps = Taps()
                val result =
                    runReconnectTap(1080, 2400, reads(*script.toTypedArray()), { _, _, _ ->
                        taps.count++
                        true
                    })
                assertEquals(ReconnectRunResult.NOT_OFF, result)
                assertEquals(0, taps.count)
            }
        }

    @Test
    fun `off then null second read is not found`() =
        runTest {
            val taps = Taps()
            val result = runReconnectTap(1080, 2400, reads(off, null), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.NOT_FOUND, result)
            assertEquals(0, taps.count)
        }

    @Test
    fun `null after a tap then on reconnects without another tap`() =
        runTest {
            val taps = Taps()
            val result = runReconnectTap(1080, 2400, reads(off, off, off, null, on), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(1, taps.count)
        }

    @Test
    fun `undelivered taps are neutral`() =
        runTest {
            var taps = 0
            val result =
                runReconnectTap(1080, 2400, reads(off), { _, _, _ ->
                    taps++
                    false
                })
            assertEquals(ReconnectRunResult.GATE_CLOSED, result)
            assertEquals(3, taps)
        }

    @Test
    fun `one undelivered then delivered tap fails normally`() =
        runTest {
            var taps = 0
            val result = runReconnectTap(1080, 2400, reads(off), { _, _, _ -> ++taps > 1 })
            assertEquals(ReconnectRunResult.FAILED, result)
            assertEquals(3, taps)
        }

    @Test
    fun `no control on the first read is not found and never taps`() =
        runTest {
            val taps = Taps()
            val result =
                runReconnectTap(1080, 2400, reads(null), { _, _, _ ->
                    taps.count++
                    true
                })
            assertEquals(ReconnectRunResult.NOT_FOUND, result)
            assertEquals(0, taps.count)
        }

    @Test
    fun `off forever fails after exactly three taps`() =
        runTest {
            val taps = Taps()
            val result =
                runReconnectTap(1080, 2400, reads(off), { _, _, _ ->
                    taps.count++
                    taps.times += currentTime
                    true
                })
            assertEquals(ReconnectRunResult.FAILED, result)
            assertEquals(3, taps.count)
            // Gap between taps: settle 4s + 4 poll gaps of 2s + pre-tap delay 0.7..2.2s.
            val gap = taps.times[1] - taps.times[0]
            assertTrue("gap $gap", gap in 12_700..14_200)
        }

    @Test
    fun `connect that finishes late reconnects with one tap`() =
        runTest {
            val taps = Taps()
            val result =
                runReconnectTap(1080, 2400, reads(off, off, off, off, off, on), { _, _, _ ->
                    taps.count++
                    true
                })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(1, taps.count)
        }

    @Test
    fun `retap only after every poll read is off`() =
        runTest {
            val taps = Taps()
            // 2 confirm + pre-tap + 5 poll reads all OFF, pre-tap of tap 2 OFF, second tap, then ON.
            val result =
                runReconnectTap(1080, 2400, reads(off, off, off, off, off, off, off, off, off, on), { _, _, _ ->
                    taps.count++
                    true
                })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(2, taps.count)
        }

    @Test
    fun `null after a tap fails without another tap`() =
        runTest {
            val taps = Taps()
            val result =
                runReconnectTap(1080, 2400, reads(off, off, off, null), { _, _, _ ->
                    taps.count++
                    true
                })
            assertEquals(ReconnectRunResult.FAILED, result)
            assertEquals(1, taps.count)
        }

    @Test
    fun `control turning on during the pre-tap pause never taps`() =
        runTest {
            val taps = Taps()
            val result = runReconnectTap(1080, 2400, reads(off, off, on), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(0, taps.count)
        }

    @Test
    fun `control turning on during the pre-tap pause of a retap never taps`() =
        runTest {
            val taps = Taps()
            // 2 confirm + pre-tap + 5 polls OFF, then ON at the retap's pre-tap read.
            val result =
                runReconnectTap(1080, 2400, reads(off, off, off, off, off, off, off, off, on), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(1, taps.count)
        }

    @Test
    fun `null on the pre-tap read never taps`() =
        runTest {
            val taps = Taps()
            val result = runReconnectTap(1080, 2400, reads(off, off, null), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.NOT_FOUND, result)
            assertEquals(0, taps.count)
        }

    @Test
    fun `tap point stays in clamp box and varies`() {
        val random = Random(7)
        val points = (1..500).map { jitteredTapPoint(off, 2000, 1000, random) }
        // clamp is 2.5% of the short side, 1000 px: 25 px.
        assertTrue(points.all { (x, y) -> x in 875..925 && y in 175..225 })
        assertTrue(points.toSet().size > 20)
    }

    @Test
    fun `tap point stays inside the ball on a 2560x1600 landscape screen`() {
        val random = Random(7)
        val points = (1..2000).map { jitteredTapPoint(off, 2560, 1600, random) }
        // The ball is 7.2% of the short side wide, so its radius is 3.6% of 1600 px: 57.6 px.
        val radius = 0.036 * 1600
        assertTrue(points.all { (x, y) -> hypot((x - off.centerX).toDouble(), (y - off.centerY).toDouble()) <= radius })
    }

    @Test
    fun `throwing check does not stop later checks`() =
        runTest {
            var runs = 0
            repeat(3) { guardedReconnectCheck({}) { if (++runs == 1) error("boom") } }
            assertEquals(3, runs)
        }

    @Test
    fun `cancellation still propagates`() =
        runTest {
            assertThrows(kotlinx.coroutines.CancellationException::class.java) {
                kotlinx.coroutines.runBlocking { guardedReconnectCheck({}) { throw kotlinx.coroutines.CancellationException("c") } }
            }
        }

    @Test
    fun `gate opens only for screen on and game package`() {
        assertTrue(reconnectGateOpen(true, "com.nianticlabs.pokemongo"))
        assertFalse(reconnectGateOpen(false, "com.nianticlabs.pokemongo"))
        assertFalse(reconnectGateOpen(true, "com.other.app"))
        assertFalse(reconnectGateOpen(true, null))
    }

    @Test
    fun `gate closes while our touch overlay is showing`() {
        val game = "com.nianticlabs.pokemongo"
        assertFalse(reconnectRunGateOpen(true, game, true))
        assertTrue(reconnectRunGateOpen(true, game, false))
        assertFalse(reconnectRunGateOpen(false, game, false))
        assertFalse(reconnectRunGateOpen(true, "com.other.app", false))
    }

    @Test
    fun `closed gate means no capture and no tap`() =
        runTest {
            var captures = 0
            var taps = 0
            val result =
                runReconnectTap(1080, 2400, {
                    captures++
                    off
                }, { _, _, _ ->
                    taps++
                    true
                }, gate = { false })
            assertEquals(ReconnectRunResult.GATE_CLOSED, result)
            assertEquals(0, captures)
            assertEquals(0, taps)
        }

    @Test
    fun `gate closing before the tap skips the tap and stays neutral`() =
        runTest {
            var taps = 0
            var gateCalls = 0
            // Gate is checked once per capture and once per tap: close it at the third check (the pre-tap read).
            val result =
                runReconnectTap(1080, 2400, reads(off), { _, _, _ ->
                    taps++
                    true
                }, gate = { ++gateCalls < 3 })
            assertEquals(ReconnectRunResult.GATE_CLOSED, result)
            assertEquals(0, taps)
        }

    @Test
    fun `our own overlay windows do not take the game off the foreground`() {
        val own = "com.locationjoystick.app"
        val game = "com.nianticlabs.pokemongo"
        val overlay = foregroundAfterWindowEvent(game, own, "androidx.compose.ui.platform.ComposeView", own)
        assertEquals(game, overlay)
        assertEquals(own, foregroundAfterWindowEvent(game, own, "$own.MainActivity", own))
        assertEquals(game, foregroundAfterWindowEvent(own, game, "x.UnityMainActivity", own))
    }
}
