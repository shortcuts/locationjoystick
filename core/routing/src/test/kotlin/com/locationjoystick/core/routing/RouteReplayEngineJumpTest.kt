package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        engine.pause()

        val target = engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})

        assertEquals(b, target)
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `jumpToNextWaypoint twice returns the third waypoint`() {
        engine.start(waypoints = waypoints, speedMs = 1.4, onPositionUpdate = {}, onComplete = {})
        engine.pause()

        engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})
        val target = engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})

        assertEquals(c, target)
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `jumpToNextWaypoint at the last waypoint is a no-op`() {
        engine.start(waypoints = waypoints, speedMs = 1.4, onPositionUpdate = {}, onComplete = {})
        engine.pause()

        repeat(3) { engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {}) }
        val target = engine.jumpToNextWaypoint(onPositionUpdate = {}, onComplete = {})

        assertEquals(d, target)
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `jumpToPreviousWaypoint at the first waypoint is a no-op`() {
        engine.start(waypoints = waypoints, speedMs = 1.4, onPositionUpdate = {}, onComplete = {})
        engine.pause()

        val target = engine.jumpToPreviousWaypoint(onPositionUpdate = {}, onComplete = {})

        assertEquals(a, target)
        kotlinx.coroutines.runBlocking { engine.stop() }
    }

    @Test
    fun `jumpToPreviousWaypoint after reaching the end walks back through every waypoint`() {
        engine.start(waypoints = waypoints, speedMs = 1.4, onPositionUpdate = {}, onComplete = {})
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
}
