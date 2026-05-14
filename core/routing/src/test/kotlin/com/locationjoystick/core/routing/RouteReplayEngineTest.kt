package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
