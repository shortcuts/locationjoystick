package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RouteReplayEngineTest {
    private val engine = RouteReplayEngine(RouteInterpolator())

    @Test
    fun `start with empty waypoints calls onComplete immediately`() {
        var completed = false
        engine.start(
            waypoints = emptyList(),
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = { completed = true },
        )
        assertTrue("onComplete should be called synchronously for empty waypoints", completed)
    }

    @Test
    fun `start with single waypoint calls onComplete immediately`() {
        var completed = false
        engine.start(
            waypoints = listOf(LatLng(48.8566, 2.3522)),
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = { completed = true },
        )
        assertTrue("onComplete should be called synchronously for single waypoint", completed)
    }

    @Test
    fun `pause after start does not throw`() {
        engine.start(
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0)),
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = {},
        )
        engine.pause()
    }

    @Test
    fun `stop after start does not throw`() {
        engine.start(
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0)),
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = {},
        )
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `resume with no prior start calls onComplete immediately`() {
        var completed = false
        engine.resume(
            onPositionUpdate = {},
            onComplete = { completed = true },
        )
        assertTrue("resume with empty savedWaypoints calls onComplete", completed)
    }

    @Test
    fun `start with two waypoints does not call onComplete synchronously`() {
        var completed = false
        engine.start(
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(0.001, 0.0)),
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = { completed = true },
        )
        assertFalse("two-waypoint replay should not complete synchronously", completed)
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `onPositionUpdate exception does not stop replay loop`() {
        val callCount = AtomicInteger(0)
        engine.start(
            // ~1500 km apart — won't complete
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(10.0, 10.0)),
            speedMs = 1.4,
            onPositionUpdate = { _ ->
                callCount.incrementAndGet()
                throw RuntimeException("simulated failure")
            },
            onComplete = {},
        )
        Thread.sleep(2500) // wait for 2 ticks (1 Hz)
        kotlinx.coroutines.runBlocking { engine.stop() }
        assertTrue("loop should continue after onPositionUpdate throws", callCount.get() >= 2)
    }

    @Test
    fun `onComplete exception does not propagate to test thread`() {
        var completed = false
        engine.start(
            // < 1 cm — snaps in first tick
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(0.0000001, 0.0)),
            speedMs = 999.0,
            onPositionUpdate = {},
            onComplete = {
                completed = true
                throw RuntimeException("simulated failure")
            },
        )
        Thread.sleep(1500)
        kotlinx.coroutines.runBlocking { engine.stop() }
        assertTrue("onComplete should have been called before throwing", completed)
    }

    @Test
    fun `position updates are called multiple times during replay`() {
        val updateCount = AtomicInteger(0)
        engine.start(
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(10.0, 10.0)),
            speedMs = 1.0,
            onPositionUpdate = { _ -> updateCount.incrementAndGet() },
            onComplete = {},
        )
        Thread.sleep(3000) // 3 ticks at 1 Hz
        kotlinx.coroutines.runBlocking { engine.stop() }
        assertTrue("should have multiple updates, got ${updateCount.get()}", updateCount.get() >= 3)
    }

    @Test
    fun `position updates advance towards target`() {
        val positions = mutableListOf<LatLng>()
        engine.start(
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(0.01, 0.0)),
            speedMs = 1.4,
            onPositionUpdate = { pos -> positions.add(pos) },
            onComplete = {},
        )
        Thread.sleep(2000)
        kotlinx.coroutines.runBlocking { engine.stop() }
        assertTrue("should have collected positions", positions.size >= 2)
        // Latitudes should increase monotonically towards target
        for (i in 1 until positions.size) {
            assertTrue(
                "lat should increase: ${positions[i - 1].latitude} <= ${positions[i].latitude}",
                positions[i].latitude >= positions[i - 1].latitude,
            )
        }
    }

    @Test
    fun `updateSpeed mid-replay changes advance distance on next tick (regression, no speed lock)`() {
        // Route replay must always honor a live speed-profile change (e.g. widget Speed
        // Cycle), even for a route started at a slow speed — there is no "locked" mode
        // that ignores updateSpeed (issue #68).
        val positions = mutableListOf<LatLng>()
        engine.start(
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(10.0, 10.0)),
            // near-stationary start speed
            speedMs = 0.1,
            onPositionUpdate = { pos -> positions.add(pos) },
            onComplete = {},
        )
        Thread.sleep(1500)
        val distanceBeforeSpeedUp = positions.last().latitude
        engine.updateSpeed(5.0) // widget Speed Cycle bump, mid-replay
        Thread.sleep(1500)
        kotlinx.coroutines.runBlocking { engine.stop() }
        val distanceAfterSpeedUp = positions.last().latitude - distanceBeforeSpeedUp
        assertTrue(
            "post-updateSpeed advance ($distanceAfterSpeedUp) should be far larger than " +
                "pre-updateSpeed advance ($distanceBeforeSpeedUp) — speed change must take effect",
            distanceAfterSpeedUp > distanceBeforeSpeedUp * 10,
        )
    }

    @Test
    fun `stop after start halts position updates`() {
        val updateCount = AtomicInteger(0)
        engine.start(
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(10.0, 10.0)),
            speedMs = 1.0,
            onPositionUpdate = { _ -> updateCount.incrementAndGet() },
            onComplete = {},
        )
        Thread.sleep(1500)
        val countBefore = updateCount.get()
        kotlinx.coroutines.runBlocking { engine.stop() }
        Thread.sleep(1500)
        val countAfter = updateCount.get()
        assertEquals("updates should not increase after stop()", countBefore, countAfter)
        assertTrue("should have had some updates before stop", countBefore > 0)
    }

    @Test
    fun `looping mode continues after reaching end`() {
        val positions = mutableListOf<LatLng>()
        val completeCount = AtomicInteger(0)
        engine.start(
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(0.0001, 0.0)),
            speedMs = 1.4,
            isLooping = true,
            onPositionUpdate = { pos -> positions.add(pos) },
            onComplete = { completeCount.incrementAndGet() },
        )
        Thread.sleep(3000)
        kotlinx.coroutines.runBlocking { engine.stop() }
        // In looping mode, onComplete should never be called
        assertEquals(0, completeCount.get())
        assertTrue("should have collected positions during looping", positions.size >= 2)
    }

    @Test
    fun `appendWaypoint adds to saved waypoints`() {
        val waypoints = listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        engine.start(
            waypoints = waypoints,
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = {},
        )
        val newWaypoint = LatLng(2.0, 0.0)
        engine.appendWaypoint(newWaypoint)
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `non-looping route calls onComplete exactly once`() {
        val latch = CountDownLatch(1)
        val completeCount = AtomicInteger(0)
        engine.start(
            // < 1 cm — snaps in first tick
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(0.0000001, 0.0)),
            speedMs = 999.0,
            isLooping = false,
            onPositionUpdate = {},
            onComplete = {
                completeCount.incrementAndGet()
                latch.countDown()
            },
        )
        assertTrue("onComplete should fire within 5 s", latch.await(5, TimeUnit.SECONDS))
        assertEquals("onComplete must fire exactly once", 1, completeCount.get())
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `pause when no active job does not throw`() {
        engine.pause()
    }

    @Test
    fun `stop when no active job does not throw`() {
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `engine remains usable after cancelActiveReplay (service restart regression)`() {
        // Regression: RouteReplayEngine is @Singleton. If close() was called from service
        // onDestroy, engineScope was permanently cancelled and subsequent replays silently
        // no-oped (green icon, no movement). cancelActiveReplay() must NOT cancel the scope.
        engine.start(
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(10.0, 10.0)),
            speedMs = 1.0,
            onPositionUpdate = {},
            onComplete = {},
        )
        engine.cancelActiveReplay() // simulates service onDestroy

        // Simulate service restart: start a new replay on the same singleton instance
        val updateCount = AtomicInteger(0)
        engine.start(
            waypoints = listOf(LatLng(0.0, 0.0), LatLng(10.0, 10.0)),
            speedMs = 1.0,
            onPositionUpdate = { _ -> updateCount.incrementAndGet() },
            onComplete = {},
        )
        Thread.sleep(2500) // wait for 2 ticks at 1 Hz
        kotlinx.coroutines.runBlocking { engine.stop() }
        assertTrue(
            "engine must emit updates after cancelActiveReplay; got ${updateCount.get()}",
            updateCount.get() >= 2,
        )
    }

    @Test
    fun `teleport between waypoints lingers at start before first hop`() {
        val start = LatLng(0.0, 0.0)
        val mid = LatLng(1.0, 0.0)
        val end = LatLng(2.0, 0.0)
        val positions = mutableListOf<LatLng>()
        engine.start(
            waypoints = listOf(start, mid, end),
            speedMs = 1.4,
            onPositionUpdate = { pos -> positions.add(pos) },
            onComplete = {},
            teleportBetweenWaypoints = true,
            teleportBetweenDelaySeconds = 2,
        )
        Thread.sleep(500)
        assertEquals(
            "must stay on stop 1 during the opening linger",
            1,
            engine.currentProgress()!!.current,
        )
        assertFalse("must not hop to stop 2 before the delay elapses", positions.contains(mid))
        Thread.sleep(2200)
        assertEquals(
            "must hop to stop 2 after lingering at the start",
            2,
            engine.currentProgress()!!.current,
        )
        assertTrue("should have hopped to the second stop", positions.contains(mid))
        assertFalse("must still linger at stop 2 before hopping to the end", positions.contains(end))
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `teleport between waypoints hops named stops then completes`() {
        val start = LatLng(0.0, 0.0)
        val mid = LatLng(1.0, 0.0)
        val end = LatLng(2.0, 0.0)
        val positions = mutableListOf<LatLng>()
        val latch = CountDownLatch(1)
        engine.start(
            waypoints = listOf(start, mid, end),
            speedMs = 1.4,
            onPositionUpdate = { pos -> positions.add(pos) },
            onComplete = { latch.countDown() },
            teleportBetweenWaypoints = true,
            teleportBetweenDelaySeconds = 0,
        )
        assertFalse(
            "must linger at least one GPS tick per hop before complete",
            latch.await(500, TimeUnit.MILLISECONDS),
        )
        assertTrue("hop replay should finish within 5 s", latch.await(5, TimeUnit.SECONDS))
        kotlinx.coroutines.runBlocking { engine.stop() }
        assertTrue("should hop to each later stop", positions.contains(mid) && positions.contains(end))
        assertTrue(
            "must not interpolate toward a far stop",
            positions.all { it == start || it == mid || it == end },
        )
    }

    @Test
    fun `teleport between planting rings walks a vertex then hops at the boundary`() {
        val ringStart = LatLng(0.0, 0.0)
        val ringVertex = LatLng(0.0000001, 0.0)
        val nextRing = LatLng(1.0, 0.0)
        val positions = mutableListOf<LatLng>()
        val latch = CountDownLatch(1)
        engine.start(
            waypoints = listOf(ringStart, ringVertex, nextRing),
            speedMs = 999.0,
            onPositionUpdate = { pos -> positions.add(pos) },
            onComplete = { latch.countDown() },
            boundaryIndices = listOf(0, 2),
            teleportBetweenWaypoints = true,
            teleportBetweenDelaySeconds = 0,
        )
        assertTrue("planting hop should finish within 5 s", latch.await(5, TimeUnit.SECONDS))
        kotlinx.coroutines.runBlocking { engine.stop() }
        assertTrue("should visit the ring-exit vertex", positions.any { it == ringVertex })
        assertEquals(nextRing, positions.last())
        assertTrue(
            "leftover carry must not walk toward the next ring before the hop",
            positions.none { it.latitude > 0.001 && it.latitude < 0.9 },
        )
    }
}
