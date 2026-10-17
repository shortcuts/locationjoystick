package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.model.GuidedPath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
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
                runReconnectTap(1080, reads(off, off, off, on), { _, _, _ ->
                    taps.count++
                    true
                })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(1, taps.count)
        }

    @Test
    fun `on null or flapping first reads never tap`() =
        runTest {
            for (script in listOf(listOf(on), listOf(null), listOf(off, on), listOf(off, null))) {
                val taps = Taps()
                val result =
                    runReconnectTap(1080, reads(*script.toTypedArray()), { _, _, _ ->
                        taps.count++
                        true
                    })
                assertEquals(ReconnectRunResult.NOT_OFF, result)
                assertEquals(0, taps.count)
            }
        }

    @Test
    fun `off forever fails after exactly three taps`() =
        runTest {
            val taps = Taps()
            val result =
                runReconnectTap(1080, reads(off), { _, _, _ ->
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
                runReconnectTap(1080, reads(off, off, off, off, off, on), { _, _, _ ->
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
                runReconnectTap(1080, reads(off, off, off, off, off, off, off, off, off, on), { _, _, _ ->
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
                runReconnectTap(1080, reads(off, off, off, null), { _, _, _ ->
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
            val result = runReconnectTap(1080, reads(off, off, on), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(0, taps.count)
        }

    @Test
    fun `control turning on during the pre-tap pause of a retap never taps`() =
        runTest {
            val taps = Taps()
            // 2 confirm + pre-tap + 5 polls OFF, then ON at the retap's pre-tap read.
            val result =
                runReconnectTap(1080, reads(off, off, off, off, off, off, off, off, on), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.RECONNECTED, result)
            assertEquals(1, taps.count)
        }

    @Test
    fun `null on the pre-tap read never taps`() =
        runTest {
            val taps = Taps()
            val result = runReconnectTap(1080, reads(off, off, null), { _, _, _ -> taps.count++ >= 0 })
            assertEquals(ReconnectRunResult.NOT_OFF, result)
            assertEquals(0, taps.count)
        }

    @Test
    fun `tap point stays in clamp box and varies`() {
        val random = Random(7)
        val points = (1..500).map { jitteredTapPoint(off, 1000, random) }
        // clamp is 2.5% of 1000 = 25 px.
        assertTrue(points.all { (x, y) -> x in 875..925 && y in 175..225 })
        assertTrue(points.toSet().size > 20)
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
    fun loopGate_pikminBloomStopsLoopButOtherPathsKeepIt() {
        assertTrue(reconnectLoopActive(true, true, true, GuidedPath.DEFAULT))
        assertTrue(reconnectLoopActive(true, true, true, GuidedPath.POKEMON_GO))
        assertFalse(reconnectLoopActive(true, true, true, GuidedPath.PIKMIN_BLOOM))
        assertFalse(reconnectLoopActive(false, true, true, GuidedPath.DEFAULT))
        assertFalse(reconnectLoopActive(true, false, true, GuidedPath.DEFAULT))
        assertFalse(reconnectLoopActive(true, true, false, GuidedPath.DEFAULT))
    }
}
