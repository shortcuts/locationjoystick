package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RouteReplayEnginePauseResumeTest {
    private val engine = RouteReplayEngine(RouteInterpolator())

    @Test
    fun `pause then resume continues replay from saved position`() {
        val positions = mutableListOf<LatLng>()
        val waypoints = listOf(LatLng(0.0, 0.0), LatLng(0.01, 0.0))

        engine.start(
            waypoints = waypoints,
            speedMs = 1.4,
            onPositionUpdate = { pos -> positions.add(pos) },
            onComplete = {},
        )

        Thread.sleep(2000)
        val positionsBeforePause = positions.size
        assertTrue("should have some positions before pause", positionsBeforePause > 0)

        engine.pause()
        Thread.sleep(1500)
        val positionsWhilePaused = positions.size
        assertEquals("no new positions while paused", positionsBeforePause, positionsWhilePaused)

        engine.resume(
            onPositionUpdate = { pos -> positions.add(pos) },
            onComplete = {},
        )
        Thread.sleep(2000)

        assertTrue("should have more positions after resume", positions.size > positionsWhilePaused)

        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `pause resume pause resume cycle works`() {
        val updateCount = AtomicInteger(0)
        val waypoints = listOf(LatLng(0.0, 0.0), LatLng(10.0, 10.0))

        engine.start(
            waypoints = waypoints,
            speedMs = 1.0,
            onPositionUpdate = { _ -> updateCount.incrementAndGet() },
            onComplete = {},
        )

        Thread.sleep(1500)
        val count1 = updateCount.get()
        assertTrue("should have updates in first run", count1 > 0)

        engine.pause()
        Thread.sleep(1000)
        val count2 = updateCount.get()
        assertEquals("no updates while paused", count1, count2)

        engine.resume(onPositionUpdate = { _ -> updateCount.incrementAndGet() }, onComplete = {})
        Thread.sleep(1500)
        val count3 = updateCount.get()
        assertTrue("should have more updates after resume", count3 > count2)

        engine.pause()
        Thread.sleep(1000)
        val count4 = updateCount.get()
        assertEquals("no updates while paused again", count3, count4)

        engine.resume(onPositionUpdate = { _ -> updateCount.incrementAndGet() }, onComplete = {})
        Thread.sleep(1500)
        val count5 = updateCount.get()
        assertTrue("should have more updates after second resume", count5 > count4)

        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `looping mode survives pause and resume`() {
        val completeCount = AtomicInteger(0)
        val waypoints = listOf(LatLng(0.0, 0.0), LatLng(0.0001, 0.0))

        engine.start(
            waypoints = waypoints,
            speedMs = 1.4,
            isLooping = true,
            onPositionUpdate = {},
            onComplete = { completeCount.incrementAndGet() },
        )

        Thread.sleep(2000)
        engine.pause()
        Thread.sleep(1000)
        engine.resume(onPositionUpdate = {}, onComplete = { completeCount.incrementAndGet() })
        Thread.sleep(2000)
        kotlinx.coroutines.runBlocking { engine.stop() }

        assertEquals(0, completeCount.get())
    }

    @Test
    fun `resume with saved position advances towards target`() {
        val positions = mutableListOf<LatLng>()
        val waypoints = listOf(LatLng(0.0, 0.0), LatLng(0.01, 0.0))

        engine.start(
            waypoints = waypoints,
            speedMs = 1.4,
            onPositionUpdate = { pos -> positions.add(pos) },
            onComplete = {},
        )

        Thread.sleep(1500)
        val lastLatBeforePause = positions.last().latitude
        engine.pause()

        Thread.sleep(500)
        engine.resume(onPositionUpdate = { pos -> positions.add(pos) }, onComplete = {})

        Thread.sleep(1500)
        val lastLatAfterResume = positions.last().latitude
        kotlinx.coroutines.runBlocking { engine.stop() }

        assertTrue("latitude should continue increasing after resume", lastLatAfterResume >= lastLatBeforePause)
    }

    @Test
    fun `pause then stop does not throw`() {
        val waypoints = listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        engine.start(
            waypoints = waypoints,
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = {},
        )

        engine.pause()
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `multiple pauses do not throw`() {
        val waypoints = listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        engine.start(
            waypoints = waypoints,
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = {},
        )

        engine.pause()
        engine.pause()
        engine.pause()

        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `resume without prior start calls onComplete`() {
        var completed = false
        engine.resume(
            onPositionUpdate = {},
            onComplete = { completed = true },
        )
        assertTrue("resume with no saved waypoints should complete immediately", completed)
    }

    @Test
    fun `start then pause then resume then stop completes cleanly`() {
        val waypoints = listOf(LatLng(0.0, 0.0), LatLng(0.001, 0.0))
        var completed = false

        engine.start(
            waypoints = waypoints,
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = { completed = true },
        )

        Thread.sleep(1000)
        engine.pause()
        Thread.sleep(500)
        engine.resume(onPositionUpdate = {}, onComplete = { completed = true })
        Thread.sleep(1000)
        kotlinx.coroutines.runBlocking { engine.stop() }

        assertFalse("should not complete during short test", completed)
    }
}
