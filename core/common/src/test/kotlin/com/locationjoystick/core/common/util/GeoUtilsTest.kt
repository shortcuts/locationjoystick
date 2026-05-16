package com.locationjoystick.core.common.util

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {
    // haversineDistance

    @Test
    fun `haversineDistance same point returns zero`() {
        val p = LatLng(51.5, 0.0)
        assertEquals(0.0, haversineDistance(p, p), 0.001)
    }

    @Test
    fun `haversineDistance 1 degree latitude is approx 111km`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(1.0, 0.0)
        assertEquals(111_195.0, haversineDistance(a, b), 500.0)
    }

    @Test
    fun `haversineDistance is symmetric`() {
        val a = LatLng(40.0, -74.0)
        val b = LatLng(51.5, 0.0)
        assertEquals(haversineDistance(a, b), haversineDistance(b, a), 0.001)
    }

    @Test
    fun `haversineDistance short distance 100m is accurate`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.0009, 0.0)
        assertEquals(100.0, haversineDistance(a, b), 2.0)
    }

    // bearingBetweenCoords

    @Test
    fun `bearingBetweenCoords due north returns 0`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(1.0, 0.0)
        assertEquals(0f, bearingBetweenCoords(a, b), 0.1f)
    }

    @Test
    fun `bearingBetweenCoords due east returns 90`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.0, 1.0)
        assertEquals(90f, bearingBetweenCoords(a, b), 0.1f)
    }

    @Test
    fun `bearingBetweenCoords due south returns 180`() {
        val a = LatLng(1.0, 0.0)
        val b = LatLng(0.0, 0.0)
        assertEquals(180f, bearingBetweenCoords(a, b), 0.1f)
    }

    @Test
    fun `bearingBetweenCoords due west returns 270`() {
        val a = LatLng(0.0, 1.0)
        val b = LatLng(0.0, 0.0)
        assertEquals(270f, bearingBetweenCoords(a, b), 0.1f)
    }

    @Test
    fun `bearingBetweenCoords result is in range 0 to 360`() {
        val a = LatLng(51.5, 0.0)
        val b = LatLng(40.7, -74.0)
        val bearing = bearingBetweenCoords(a, b)
        assertTrue("bearing $bearing out of range [0,360)", bearing >= 0f && bearing < 360f)
    }

    // interpolatePosition

    @Test
    fun `interpolatePosition fraction 0 returns from`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(10.0, 20.0)
        assertEquals(from, interpolatePosition(from, to, 0.0))
    }

    @Test
    fun `interpolatePosition fraction 1 returns to`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(10.0, 20.0)
        assertEquals(to, interpolatePosition(from, to, 1.0))
    }

    @Test
    fun `interpolatePosition fraction 0_5 returns midpoint`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(10.0, 20.0)
        val mid = interpolatePosition(from, to, 0.5)
        assertEquals(5.0, mid.latitude, 0.0001)
        assertEquals(10.0, mid.longitude, 0.0001)
    }

    @Test
    fun `interpolatePosition fraction 0_25 is quarter way`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(4.0, 8.0)
        val result = interpolatePosition(from, to, 0.25)
        assertEquals(1.0, result.latitude, 0.0001)
        assertEquals(2.0, result.longitude, 0.0001)
    }

    // snapBearingToCardinal

    @Test
    fun `snapBearingToCardinal snap false returns original`() {
        assertEquals(47.3f, snapBearingToCardinal(47.3f, false), 0.001f)
    }

    @Test
    fun `snapBearingToCardinal 0 degrees snaps to 0`() {
        assertEquals(0f, snapBearingToCardinal(0f, true), 0.001f)
    }

    @Test
    fun `snapBearingToCardinal 22 degrees rounds down to 0`() {
        assertEquals(0f, snapBearingToCardinal(22f, true), 0.001f)
    }

    @Test
    fun `snapBearingToCardinal 23 degrees rounds up to 45`() {
        assertEquals(45f, snapBearingToCardinal(23f, true), 0.001f)
    }

    @Test
    fun `snapBearingToCardinal 45 degrees stays 45`() {
        assertEquals(45f, snapBearingToCardinal(45f, true), 0.001f)
    }

    @Test
    fun `snapBearingToCardinal 90 degrees stays 90`() {
        assertEquals(90f, snapBearingToCardinal(90f, true), 0.001f)
    }

    @Test
    fun `snapBearingToCardinal 315 degrees stays 315`() {
        assertEquals(315f, snapBearingToCardinal(315f, true), 0.001f)
    }

    @Test
    fun `snapBearingToCardinal 338 degrees wraps to 0`() {
        // round(338/45)=round(7.51)=8 → 8*45=360 → 360%360=0
        assertEquals(0f, snapBearingToCardinal(338f, true), 0.001f)
    }

    @Test
    fun `snapBearingToCardinal snap result is always multiple of 45`() {
        listOf(10f, 55f, 100f, 170f, 200f, 260f, 300f, 350f).forEach { bearing ->
            val snapped = snapBearingToCardinal(bearing, true)
            assertEquals(0f, snapped % 45f, 0.001f)
        }
    }

    // randomPointInRadius

    @Test
    fun `randomPointInRadius point is within specified radius`() {
        val center = LatLng(51.5, 0.0)
        val radius = 1000.0
        repeat(50) {
            val point = randomPointInRadius(center, radius)
            val dist = haversineDistance(center, point)
            assertTrue("dist $dist exceeds radius $radius", dist <= radius + 1.0)
        }
    }

    @Test
    fun `randomPointInRadius zero radius returns center`() {
        val center = LatLng(51.5, -0.12)
        val point = randomPointInRadius(center, 0.0)
        assertEquals(center.latitude, point.latitude, 0.0001)
        assertEquals(center.longitude, point.longitude, 0.0001)
    }

    @Test
    fun `randomPointInRadius produces varied points`() {
        val center = LatLng(0.0, 0.0)
        val points = (1..50).map { randomPointInRadius(center, 1000.0) }
        val uniqueLats = points.map { it.latitude }.toSet()
        assertTrue("expected multiple distinct latitudes", uniqueLats.size > 10)
    }

    // addGpsJitter

    @Test
    fun `addGpsJitter returns point within jitter radius`() {
        val original = LatLng(48.8566, 2.3522)
        val maxJitter = 2.0
        repeat(50) {
            val jittered = addGpsJitter(original, maxJitter)
            val dist = haversineDistance(original, jittered)
            assertTrue("jitter $dist exceeds max $maxJitter", dist <= maxJitter + 0.5)
        }
    }

    @Test
    fun `addGpsJitter with zero max returns near center`() {
        val original = LatLng(0.0, 0.0)
        val jittered = addGpsJitter(original, 0.0)
        assertEquals(original.latitude, jittered.latitude, 0.0001)
        assertEquals(original.longitude, jittered.longitude, 0.0001)
    }

    @Test
    fun `addGpsJitter default param uses 1_5 meters`() {
        val original = LatLng(51.5, 0.0)
        repeat(30) {
            val jittered = addGpsJitter(original)
            val dist = haversineDistance(original, jittered)
            assertTrue("default jitter $dist exceeds 1.5m", dist <= 1.5 + 0.5)
        }
    }

    // metersToLatDegrees

    @Test
    fun `metersToLatDegrees 111195m is approx 1 degree`() {
        val degrees = metersToLatDegrees(111_195.0)
        assertEquals(1.0, degrees, 0.01)
    }

    @Test
    fun `metersToLatDegrees zero meters returns zero`() {
        assertEquals(0.0, metersToLatDegrees(0.0), 0.0001)
    }

    @Test
    fun `metersToLatDegrees 1000m is less than 0_01 degrees`() {
        val degrees = metersToLatDegrees(1000.0)
        assertTrue("1000m should be less than 0.01 degrees", degrees < 0.01)
    }

    // metersToLngDegrees

    @Test
    fun `metersToLngDegrees at equator approx 1 degree`() {
        val degrees = metersToLngDegrees(111_195.0, 0.0)
        assertEquals(1.0, degrees, 0.01)
    }

    @Test
    fun `metersToLngDegrees increases with latitude distance`() {
        val at0 = metersToLngDegrees(1000.0, 0.0)
        val at45 = metersToLngDegrees(1000.0, 45.0)
        assertTrue("same meters = more degrees at higher latitude", at45 > at0)
    }

    @Test
    fun `metersToLngDegrees zero meters returns zero`() {
        assertEquals(0.0, metersToLngDegrees(0.0, 45.0), 0.0001)
    }
}
