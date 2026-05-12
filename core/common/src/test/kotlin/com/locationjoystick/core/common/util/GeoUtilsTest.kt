package com.locationjoystick.core.common.util

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

    // bearingBetween

    @Test
    fun `bearingBetween due north returns 0`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(1.0, 0.0)
        assertEquals(0f, bearingBetween(a, b), 0.1f)
    }

    @Test
    fun `bearingBetween due east returns 90`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.0, 1.0)
        assertEquals(90f, bearingBetween(a, b), 0.1f)
    }

    @Test
    fun `bearingBetween due south returns 180`() {
        val a = LatLng(1.0, 0.0)
        val b = LatLng(0.0, 0.0)
        assertEquals(180f, bearingBetween(a, b), 0.1f)
    }

    @Test
    fun `bearingBetween due west returns 270`() {
        val a = LatLng(0.0, 1.0)
        val b = LatLng(0.0, 0.0)
        assertEquals(270f, bearingBetween(a, b), 0.1f)
    }

    @Test
    fun `bearingBetween result is in range 0 to 360`() {
        val a = LatLng(51.5, 0.0)
        val b = LatLng(40.7, -74.0)
        val bearing = bearingBetween(a, b)
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
}
