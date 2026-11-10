package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.common.constants.AppConstants.ReconnectTapConstants
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
    private val search = ReconnectControl(ReconnectState.SEARCHING, 900, 200)

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
    fun `an undelivered tap is neutral and is not repeated`() =
        runTest {
            var taps = 0
            val result =
                runReconnectTap(1080, 2400, reads(off), { _, _, _ ->
                    taps++
                    false
                })
            assertEquals(ReconnectRunResult.GATE_CLOSED, result)
            assertEquals(1, taps)
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
    fun `no control twice waits 10s then is not found and never taps`() =
        runTest {
            val taps = Taps()
            val result =
                runReconnectTap(1080, 2400, reads(null), { _, _, _ ->
                    taps.count++
                    true
                })
            assertEquals(ReconnectRunResult.NOT_FOUND, result)
            assertEquals(0, taps.count)
            assertEquals(ReconnectTapConstants.UNSURE_RETRY_DELAY_MS, currentTime)
        }

    @Test
    fun `null first read then the retry confirms and reconnects with one tap`() =
        runTest {
            val taps = Taps()
            val result =
                runReconnectTap(1080, 2400, reads(null, off, off, off, on), { _, _, _ ->
                    taps.count++
                    true
                })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(1, taps.count)
            assertTrue("elapsed $currentTime", currentTime >= ReconnectTapConstants.UNSURE_RETRY_DELAY_MS)
        }

    @Test
    fun `null second read then the retry confirms and reconnects with one tap`() =
        runTest {
            val taps = Taps()
            val result =
                runReconnectTap(1080, 2400, reads(off, null, off, off, off, on), { _, _, _ ->
                    taps.count++
                    true
                })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(1, taps.count)
        }

    @Test
    fun `null then on during the retry is not off and never taps`() =
        runTest {
            val taps = Taps()
            val result = runReconnectTap(1080, 2400, reads(null, on), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.NOT_OFF, result)
            assertEquals(0, taps.count)
        }

    @Test
    fun `gate closing during the retry wait ends gate closed`() =
        runTest {
            var gateCalls = 0
            val result =
                runReconnectTap(1080, 2400, reads(null), { _, _, _ -> true }, gate = { ++gateCalls < 2 })
            assertEquals(ReconnectRunResult.GATE_CLOSED, result)
        }

    @Test
    fun `search ring before the first read ends then off taps once after the ring`() =
        runTest {
            val taps = Taps()
            val result =
                runReconnectTap(1080, 2400, reads(search, search, search, off, off, off, on), { _, _, _ ->
                    taps.count++
                    taps.times += currentTime
                    true
                })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(1, taps.count)
            assertTrue("tap at ${taps.times[0]}", taps.times[0] >= 3 * ReconnectTapConstants.SEARCH_POLL_INTERVAL_MS)
        }

    @Test
    fun `search ring on the pre tap read never taps`() =
        runTest {
            val taps = Taps()
            val result = runReconnectTap(1080, 2400, reads(off, off, search, search, on), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(0, taps.count)
        }

    @Test
    fun `search ring after a tap then on reconnects with one tap`() =
        runTest {
            val taps = Taps()
            val result = runReconnectTap(1080, 2400, reads(off, off, off, search, search, on), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(1, taps.count)
        }

    @Test
    fun `search ring that outlasts the cap before any tap is not found`() =
        runTest {
            val taps = Taps()
            val result = runReconnectTap(1080, 2400, reads(search), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.NOT_FOUND, result)
            assertEquals(0, taps.count)
        }

    @Test
    fun `search ring that outlasts the cap after a tap fails with one tap`() =
        runTest {
            val taps = Taps()
            val result = runReconnectTap(1080, 2400, reads(off, off, off, search), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.FAILED, result)
            assertEquals(1, taps.count)
        }

    @Test
    fun `off forever fails after exactly one tap and a 10 s wait plus 2 reads`() =
        runTest {
            val taps = Taps()
            var reads = 0
            val result =
                runReconnectTap(1080, 2400, {
                    reads++
                    off
                }, { _, _, _ ->
                    taps.count++
                    taps.times += currentTime
                    true
                })
            assertEquals(ReconnectRunResult.FAILED, result)
            assertEquals(1, taps.count)
            assertEquals(5, reads)
            assertEquals(
                ReconnectTapConstants.POST_TAP_WAIT_MS + ReconnectTapConstants.SETTLE_POLL_INTERVAL_MS,
                currentTime - taps.times[0],
            )
        }

    @Test
    fun `pre-tap pause keeps its jitter`() =
        runTest {
            val taps = Taps()
            runReconnectTap(1080, 2400, reads(off), { _, _, _ ->
                taps.count++
                taps.times += currentTime
                true
            }, random = Random(7))
            // The first confirm read sits 400 ms in, so the tap lands after 400 ms plus its pause.
            val pause = taps.times[0] - ReconnectTapConstants.CONFIRM_DELAY_MS
            assertTrue("pause $pause", pause in 700..2_200)
        }

    @Test
    fun `second post-tap read on reconnects with one tap`() =
        runTest {
            val taps = Taps()
            val result =
                runReconnectTap(1080, 2400, reads(off, off, off, off, on), { _, _, _ ->
                    taps.count++
                    true
                })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(1, taps.count)
        }

    @Test
    fun `connect that reads on 16 s after the tap fails without a second tap`() =
        runTest {
            val taps = Taps()
            val result =
                runReconnectTap(1080, 2400, {
                    if (taps.times.isNotEmpty() && currentTime - taps.times[0] >= 16_000) on else off
                }, { _, _, _ ->
                    taps.count++
                    taps.times += currentTime
                    true
                })
            assertEquals(ReconnectRunResult.FAILED, result)
            assertEquals(1, taps.count)
        }

    @Test
    fun `tap not allowed and control off is paused without a tap`() =
        runTest {
            val taps = Taps()
            val result = runReconnectTap(1080, 2400, reads(off), { _, _, _ -> taps.count++ >= 0 }, allowTap = false)
            assertEquals(ReconnectRunResult.PAUSED, result)
            assertEquals(0, taps.count)
        }

    @Test
    fun `tap not allowed and control on is not off`() =
        runTest {
            val taps = Taps()
            val result = runReconnectTap(1080, 2400, reads(on), { _, _, _ -> taps.count++ >= 0 }, allowTap = false)
            assertEquals(ReconnectRunResult.NOT_OFF, result)
            assertEquals(0, taps.count)
        }

    @Test
    fun `tap not allowed and no control is not found`() =
        runTest {
            val result = runReconnectTap(1080, 2400, reads(null), { _, _, _ -> true }, allowTap = false)
            assertEquals(ReconnectRunResult.NOT_FOUND, result)
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
