package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// start() dispatches the replay tick loop onto Dispatchers.Default; pausing immediately after
// races whether that first tick has run yet, which flaked on CI (0.23.0 release build) though
// never locally. Waiting it out makes pause() land after the tick deterministically instead of
// racing it — the tick's tiny move never crosses a waypoint boundary, so assertions are unaffected.
private const val SETTLE_MS = 50L

class RouteReplayEngineJumpTest {
    private val engine = RouteReplayEngine(RouteInterpolator())

    private val a = LatLng(0.0, 0.0)
    private val b = LatLng(0.001, 0.0)
    private val c = LatLng(0.002, 0.0)
    private val d = LatLng(0.003, 0.0)
    private val waypoints = listOf(a, b, c, d)

    @Test
    fun `jumpToNextWaypoint after start returns the second waypoint`() {
        engine.start(waypoints = waypoints, speedMs = 1.4, onPositionUpdate = {}, onComplete = {})
        Thread.sleep(SETTLE_MS)
        engine.pause()

        val target = engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})

        assertEquals(b, target)
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `jumpToNextWaypoint twice returns the third waypoint`() {
        engine.start(waypoints = waypoints, speedMs = 1.4, onPositionUpdate = {}, onComplete = {})
        Thread.sleep(SETTLE_MS)
        engine.pause()

        engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})
        val target = engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})

        assertEquals(c, target)
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `jumpToNextWaypoint at the last waypoint is a no-op`() {
        engine.start(waypoints = waypoints, speedMs = 1.4, onPositionUpdate = {}, onComplete = {})
        Thread.sleep(SETTLE_MS)
        engine.pause()

        repeat(3) { engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {}) }
        val target = engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})

        assertEquals(d, target)
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `jumpToPreviousWaypoint at the first waypoint is a no-op`() {
        engine.start(waypoints = waypoints, speedMs = 1.4, onPositionUpdate = {}, onComplete = {})
        Thread.sleep(SETTLE_MS)
        engine.pause()

        val target = engine.jumpToPreviousWaypoint(onPositionUpdate = {}, onComplete = {})

        assertEquals(a, target)
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `jumpToPreviousWaypoint after reaching the end walks back through every waypoint`() {
        engine.start(waypoints = waypoints, speedMs = 1.4, onPositionUpdate = {}, onComplete = {})
        Thread.sleep(SETTLE_MS)
        engine.pause()
        repeat(3) { engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {}) }

        val first = engine.jumpToPreviousWaypoint(onPositionUpdate = {}, onComplete = {})
        val second = engine.jumpToPreviousWaypoint(onPositionUpdate = {}, onComplete = {})
        val third = engine.jumpToPreviousWaypoint(onPositionUpdate = {}, onComplete = {})

        assertEquals(c, first)
        assertEquals(b, second)
        assertEquals(a, third)
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `jumpToNextWaypoint with no active replay returns null`() {
        val target = engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})

        assertNull(target)
    }

    @Test
    fun `jumpToNextWaypoint lands only on boundary points when the path is road-expanded`() {
        // 4 real waypoints (indices 0, 2, 5, 7 in the expanded, road-following path) with
        // extra OSRM via-points interleaved between them.
        // Real waypoints a, b, c, d sit at expanded indices 0, 2, 5, 7 below.
        val expanded =
            listOf(
                a,
                LatLng(0.0002, 0.0),
                b,
                LatLng(0.0012, 0.0),
                LatLng(0.0015, 0.0),
                c,
                LatLng(0.0025, 0.0),
                d,
            )
        val boundaries = listOf(0, 2, 5, 7)
        engine.start(
            waypoints = expanded,
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = {},
            boundaryIndices = boundaries,
        )
        Thread.sleep(SETTLE_MS)
        engine.pause()

        val next = engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})
        assertEquals(b, next)

        val nextAgain = engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})
        assertEquals(c, nextAgain)

        val previous = engine.jumpToPreviousWaypoint(onPositionUpdate = {}, onComplete = {})
        assertEquals(b, previous)

        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `jumpToNextWaypoint while running resumes ticking toward the following waypoint`() {
        val positions = mutableListOf<LatLng>()
        engine.start(waypoints = waypoints, speedMs = 1.4, onPositionUpdate = { pos -> positions.add(pos) }, onComplete = {})

        Thread.sleep(200)
        engine.jumpToNextWaypoint(onPositionUpdate = { pos -> positions.add(pos) }, onComplete = {})
        val countAfterJump = positions.size

        Thread.sleep(1500)

        assertTrue("should have more positions after jump", positions.size > countAfterJump)
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `currentProgress at start is 1 of N named stops`() {
        engine.start(waypoints = waypoints, speedMs = 1.4, onPositionUpdate = {}, onComplete = {})
        Thread.sleep(SETTLE_MS)
        engine.pause()

        val progress = engine.currentProgress()
        assertEquals(1, progress!!.current)
        assertEquals(4, progress.total)
        assertEquals("1/4", progress.label)

        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `currentProgress after jumpToNextWaypoint is 2 of N`() {
        engine.start(waypoints = waypoints, speedMs = 1.4, onPositionUpdate = {}, onComplete = {})
        Thread.sleep(SETTLE_MS)
        engine.pause()
        engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})

        val progress = engine.currentProgress()
        assertEquals(2, progress!!.current)
        assertEquals(4, progress.total)

        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `currentProgress uses named-stop boundaries on a road-expanded path`() {
        val expanded =
            listOf(
                a,
                LatLng(0.0002, 0.0),
                b,
                LatLng(0.0012, 0.0),
                LatLng(0.0015, 0.0),
                c,
                LatLng(0.0025, 0.0),
                d,
            )
        engine.start(
            waypoints = expanded,
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = {},
            boundaryIndices = listOf(0, 2, 5, 7),
        )
        Thread.sleep(SETTLE_MS)
        engine.pause()

        assertEquals(1, engine.currentProgress()!!.current)
        assertEquals(4, engine.currentProgress()!!.total)

        engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})
        assertEquals(2, engine.currentProgress()!!.current)

        kotlinx.coroutines.runBlocking { engine.stop() }
        assertNull(engine.currentProgress())
    }

    @Test
    fun `jumpToNextWaypoint during hop linger stays on the next stop instead of hopping twice`() {
        engine.start(
            waypoints = waypoints,
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = {},
            teleportBetweenWaypoints = true,
            teleportBetweenDelaySeconds = 0,
        )
        Thread.sleep(200)
        assertEquals("should linger at stop 1 before the first automatic hop", 1, engine.currentProgress()!!.current)
        Thread.sleep(1500)
        assertEquals("should be lingering at stop 2 after the first hop", 2, engine.currentProgress()!!.current)

        val target = engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})
        assertEquals(c, target)
        Thread.sleep(300)

        assertEquals(
            "next should land on stop 3 and linger; hopping again would show 4/4",
            3,
            engine.currentProgress()!!.current,
        )
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `jumpToPreviousWaypoint during hop linger stays on the previous stop instead of hopping forward`() {
        engine.start(
            waypoints = waypoints,
            speedMs = 1.4,
            onPositionUpdate = {},
            onComplete = {},
            teleportBetweenWaypoints = true,
            teleportBetweenDelaySeconds = 0,
        )
        Thread.sleep(200)
        assertEquals("should linger at stop 1 before the first automatic hop", 1, engine.currentProgress()!!.current)
        Thread.sleep(1500)
        assertEquals("should be lingering at stop 2 after the first hop", 2, engine.currentProgress()!!.current)

        val target = engine.jumpToPreviousWaypoint(onPositionUpdate = {}, onComplete = {})
        assertEquals(a, target)
        Thread.sleep(300)

        assertEquals(
            "previous should land on stop 1 and linger; hopping forward would show 2/4 again",
            1,
            engine.currentProgress()!!.current,
        )
        kotlinx.coroutines.runBlocking { engine.stop() }
    }
}
