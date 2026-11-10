package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RouteProgress
import com.locationjoystick.core.model.Waypoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TeleportRouteEngineTest {
    private val engine = TeleportRouteEngine()
    private val pointA = LatLng(0.0, 0.0)
    private val pointB = LatLng(1.0, 0.0)
    private val pointC = LatLng(2.0, 0.0)

    private fun wp(
        position: LatLng,
        waitSeconds: Int,
    ) = Waypoint(id = "", position = position, orderIndex = 0, waitSeconds = waitSeconds)

    @Test
    fun `start reports first waypoint position with no delay`() {
        val latch = CountDownLatch(1)
        engine.start(
            waypoints = listOf(wp(pointA, 5), wp(pointB, 5)),
            onPositionUpdate = { if (it == pointA) latch.countDown() },
            onComplete = {},
        )
        // Well under the 1 s tick interval — proves the first update isn't waiting on a delay.
        assertTrue("first update must arrive before the first tick delay", latch.await(300, TimeUnit.MILLISECONDS))
        runBlocking { engine.stop() }
    }

    @Test
    fun `stays frozen at waypoint until wait elapses then jumps`() {
        val positions = mutableListOf<LatLng>()
        engine.start(
            waypoints = listOf(wp(pointA, 2), wp(pointB, 2)),
            onPositionUpdate = { positions.add(it) },
            onComplete = {},
        )
        Thread.sleep(1500) // mid-wait: 1 tick in, still under 2s
        assertTrue("should still be frozen at A", positions.all { it == pointA })
        Thread.sleep(1000) // now past 2s total
        runBlocking { engine.stop() }
        assertTrue("should have jumped to B", positions.contains(pointB))
    }

    @Test
    fun `each waypoint honors its own wait duration independently`() {
        val jumpToB = CountDownLatch(1)
        val jumpToC = CountDownLatch(1)
        engine.start(
            waypoints = listOf(wp(pointA, 1), wp(pointB, 2), wp(pointC, 1)),
            onPositionUpdate = {
                if (it == pointB) jumpToB.countDown()
                if (it == pointC) jumpToC.countDown()
            },
            onComplete = {},
        )
        assertTrue("A waits 1s, should reach B within 2s", jumpToB.await(2, TimeUnit.SECONDS))
        assertTrue("B waits 2s, should reach C within 3s", jumpToC.await(3, TimeUnit.SECONDS))
        runBlocking { engine.stop() }
    }

    @Test
    fun `non-looping completes once after last wait and stops ticking`() {
        val completeCount = AtomicInteger(0)
        val latch = CountDownLatch(1)
        engine.start(
            waypoints = listOf(wp(pointA, 1), wp(pointB, 1)),
            isLooping = false,
            onPositionUpdate = {},
            onComplete = {
                completeCount.incrementAndGet()
                latch.countDown()
            },
        )
        assertTrue("onComplete should fire", latch.await(4, TimeUnit.SECONDS))
        Thread.sleep(1500)
        assertEquals("onComplete must fire exactly once", 1, completeCount.get())
        runBlocking { engine.stop() }
    }

    @Test
    fun `looping jumps back to first waypoint and never completes`() {
        val completeCount = AtomicInteger(0)
        val visitedA = CountDownLatch(2) // start + one loop-back
        engine.start(
            waypoints = listOf(wp(pointA, 1), wp(pointB, 1)),
            isLooping = true,
            onPositionUpdate = { if (it == pointA) visitedA.countDown() },
            onComplete = { completeCount.incrementAndGet() },
        )
        assertTrue("should loop back to A", visitedA.await(4, TimeUnit.SECONDS))
        runBlocking { engine.stop() }
        assertEquals(0, completeCount.get())
    }

    @Test
    fun `pause then resume preserves remaining wait instead of resetting it`() {
        val jumpedToB = CountDownLatch(1)
        engine.start(
            waypoints = listOf(wp(pointA, 3), wp(pointB, 3)),
            onPositionUpdate = {},
            onComplete = {},
        )
        Thread.sleep(2200) // just past the 2nd tick: ~1s (or less) remaining of the 3s wait
        engine.pause()
        Thread.sleep(1500) // paused: must NOT count toward the wait

        engine.resume(
            onPositionUpdate = { if (it == pointB) jumpedToB.countDown() },
            onComplete = {},
        )
        // Only ~1s of wait was left before pause. A reset-to-full-3s wait would still be
        // waiting at 2s post-resume; a preserved remaining jumps well before that.
        assertTrue("should jump to B quickly — remaining wait must be preserved, not reset", jumpedToB.await(2000, TimeUnit.MILLISECONDS))
        runBlocking { engine.stop() }
    }

    @Test
    fun `jumpToNextWaypoint teleports immediately and resets target wait to full`() {
        engine.start(
            waypoints = listOf(wp(pointA, 10), wp(pointB, 10), wp(pointC, 10)),
            onPositionUpdate = {},
            onComplete = {},
        )
        val landedOnC = CountDownLatch(1)
        val target = engine.jumpToNextWaypoint(onPositionUpdate = { if (it == pointC) landedOnC.countDown() }, onComplete = {})
        assertEquals(pointB, target)

        // B's own wait is 10s — resuming from B must not immediately jump onward to C.
        assertTrue("should NOT reach C within 1.5s after jumping to B", !landedOnC.await(1500, TimeUnit.MILLISECONDS))
        runBlocking { engine.stop() }
    }

    @Test
    fun `jumping back mid-wait resets the landed waypoint's timer to full (issue scenario)`() {
        val visitedB = CountDownLatch(1)
        engine.start(
            waypoints = listOf(wp(pointA, 1), wp(pointB, 10)),
            onPositionUpdate = { if (it == pointB) visitedB.countDown() },
            onComplete = {},
        )
        // Let it jump A -> B naturally (A only waits 1s).
        assertTrue(visitedB.await(3, TimeUnit.SECONDS))
        Thread.sleep(1500) // now mid-wait at B, well into its 10s window

        val backAt = CountDownLatch(1)
        val forwardAgain = CountDownLatch(1)
        val jumpTime = System.nanoTime()
        val target =
            engine.jumpToPreviousWaypoint(
                onPositionUpdate = {
                    if (it == pointA) backAt.countDown()
                    if (it == pointB) forwardAgain.countDown()
                },
                onComplete = {},
            )
        assertEquals(pointA, target)
        assertTrue(backAt.await(1, TimeUnit.SECONDS))

        // A's wait is 1s. If the timer had NOT been reset (e.g. still carrying leftover
        // state from the original A visit), it would jump forward again instantly. Confirm
        // it instead takes close to the full fresh 1s before jumping forward again.
        assertTrue(forwardAgain.await(3, TimeUnit.SECONDS))
        val elapsedMs = (System.nanoTime() - jumpTime) / 1_000_000
        assertTrue(
            "should take close to the full fresh 1s wait at A, not instantly ($elapsedMs ms)",
            elapsedMs in 600..2500,
        )
        runBlocking { engine.stop() }
    }

    @Test
    fun `stop clears state so a subsequent start behaves like a fresh start`() {
        engine.start(
            waypoints = listOf(wp(pointA, 10), wp(pointB, 10)),
            onPositionUpdate = {},
            onComplete = {},
        )
        Thread.sleep(500)
        runBlocking { engine.stop() }

        val latch = CountDownLatch(1)
        val positions = mutableListOf<LatLng>()
        engine.start(
            waypoints = listOf(wp(pointB, 1), wp(pointC, 1)),
            onPositionUpdate = {
                positions.add(it)
                latch.countDown()
            },
            onComplete = {},
        )
        assertTrue("fresh start must report a position promptly", latch.await(1, TimeUnit.SECONDS))
        assertEquals("fresh start must report the new first waypoint, not stale resumeIndex", pointB, positions.first())
        runBlocking { engine.stop() }
    }

    @Test
    fun `start with empty waypoints calls onComplete immediately`() {
        var completed = false
        engine.start(
            waypoints = emptyList(),
            onPositionUpdate = {},
            onComplete = { completed = true },
        )
        assertTrue(completed)
    }

    @Test
    fun `start with single waypoint calls onComplete immediately`() {
        var completed = false
        engine.start(
            waypoints = listOf(wp(pointA, 5)),
            onPositionUpdate = {},
            onComplete = { completed = true },
        )
        assertTrue(completed)
    }

    @Test
    fun `currentProgress is null idle, 1 of N after start and 2 of N after a jump`() {
        assertEquals(null, engine.currentProgress())
        engine.start(
            waypoints = listOf(wp(pointA, 60), wp(pointB, 60), wp(pointC, 60)),
            onPositionUpdate = {},
            onComplete = {},
        )
        assertEquals(RouteProgress(1, 3), engine.currentProgress())
        engine.pause()
        engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})
        assertEquals(RouteProgress(2, 3), engine.currentProgress())
        runBlocking { engine.stop() }
    }
}
