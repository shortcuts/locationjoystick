package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.distanceTo
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RoamingEngineTest {
    private val engine = RoamingEngine(OsrmClient(), RouteInterpolator())

    @Test
    fun `randomPointInRadius stays within radius`() {
        val center = LatLng(48.8566, 2.3522)
        val radiusMeters = 500.0
        repeat(200) {
            val point = engine.randomPointInRadius(center, radiusMeters)
            val dist = center.distanceTo(point)
            assertTrue(
                "point at dist=$dist exceeds radius=$radiusMeters",
                dist <= radiusMeters + 10.0,
            )
        }
    }

    @Test
    fun `randomPointInRadius zero radius returns center`() {
        val center = LatLng(51.5074, -0.1278)
        repeat(10) {
            val point = engine.randomPointInRadius(center, 0.0)
            assertTrue(abs(point.latitude - center.latitude) < 0.0001)
            assertTrue(abs(point.longitude - center.longitude) < 0.0001)
        }
    }

    @Test
    fun `randomPointInRadius produces varied output`() {
        val center = LatLng(0.0, 0.0)
        val points = (1..20).map { engine.randomPointInRadius(center, 1000.0) }
        val uniqueLats = points.map { it.latitude }.toSet()
        assertTrue("expected multiple distinct points", uniqueLats.size > 1)
    }

    @Test
    fun `stopRoaming does not throw when not started`() {
        kotlinx.coroutines.runBlocking { engine.stopRoaming() }
    }
}
