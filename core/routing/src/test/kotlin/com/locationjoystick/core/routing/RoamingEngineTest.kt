package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.model.distanceTo
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    @Test
    fun `randomPointInRadius distribution is roughly uniform`() {
        val center = LatLng(0.0, 0.0)
        val radiusMeters = 1000.0
        val points = (1..100).map { engine.randomPointInRadius(center, radiusMeters) }

        // Check points spread across quadrants
        val ne = points.count { it.latitude > 0.0 && it.longitude > 0.0 }
        val nw = points.count { it.latitude > 0.0 && it.longitude < 0.0 }
        val se = points.count { it.latitude < 0.0 && it.longitude > 0.0 }
        val sw = points.count { it.latitude < 0.0 && it.longitude < 0.0 }

        // Each quadrant should have ~25 points (±10 tolerance)
        assertTrue("NE quadrant should have ~25 points, got $ne", ne in 15..35)
        assertTrue("NW quadrant should have ~25 points, got $nw", nw in 15..35)
        assertTrue("SE quadrant should have ~25 points, got $se", se in 15..35)
        assertTrue("SW quadrant should have ~25 points, got $sw", sw in 15..35)
    }

    @Test
    fun `randomPointInRadius respects maximum radius strictly`() {
        val center = LatLng(51.5, 0.0)
        val radius = 100.0
        repeat(100) {
            val point = engine.randomPointInRadius(center, radius)
            val dist = center.distanceTo(point)
            assertTrue("distance $dist must not exceed radius $radius", dist <= radius)
        }
    }

    @Test
    fun `stop does not permanently break startRoaming`() {
        // Regression for: stop() used to cancel engineScope permanently making startRoaming a no-op.
        val config =
            RoamingConfig(
                centerPosition = LatLng(0.0, 0.0),
                radiusMeters = 100.0,
                distanceMeters = 50.0,
                useRoadSnapping = false,
                speedProfileId = "walk",
                returnToInitialLocation = false,
            )
        // stop() should not kill the engine scope
        engine.stop()

        val latch = CountDownLatch(1)
        engine.startRoaming(config, 1.4) { latch.countDown() }
        val received = latch.await(5, TimeUnit.SECONDS)
        kotlinx.coroutines.runBlocking { engine.stopRoaming() }

        assertTrue("startRoaming should emit after stop()", received)
    }
}
