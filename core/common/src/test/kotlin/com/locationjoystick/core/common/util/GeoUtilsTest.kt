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
        assertEquals(0.0, calculateBearing(a.latitude, a.longitude, b.latitude, b.longitude), 0.1)
    }

    @Test
    fun `bearingBetweenCoords due east returns 90`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.0, 1.0)
        assertEquals(90.0, calculateBearing(a.latitude, a.longitude, b.latitude, b.longitude), 0.1)
    }

    @Test
    fun `bearingBetweenCoords due south returns 180`() {
        val a = LatLng(1.0, 0.0)
        val b = LatLng(0.0, 0.0)
        assertEquals(180.0, calculateBearing(a.latitude, a.longitude, b.latitude, b.longitude), 0.1)
    }

    @Test
    fun `bearingBetweenCoords due west returns 270`() {
        val a = LatLng(0.0, 1.0)
        val b = LatLng(0.0, 0.0)
        assertEquals(270.0, calculateBearing(a.latitude, a.longitude, b.latitude, b.longitude), 0.1)
    }

    @Test
    fun `bearingBetweenCoords result is in range 0 to 360`() {
        val a = LatLng(51.5, 0.0)
        val b = LatLng(40.7, -74.0)
        val bearing = calculateBearing(a.latitude, a.longitude, b.latitude, b.longitude)
        assertTrue("bearing $bearing out of range [0,360)", bearing >= 0.0 && bearing < 360.0)
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

    // haversineDistance raw coordinate overload

    @Test
    fun `haversineDistance raw coordinates same point returns zero`() {
        assertEquals(0.0, haversineDistance(51.5, 0.0, 51.5, 0.0), 0.001)
    }

    @Test
    fun `haversineDistance raw coordinates 1 degree latitude is approx 111km`() {
        assertEquals(111_195.0, haversineDistance(0.0, 0.0, 1.0, 0.0), 500.0)
    }

    @Test
    fun `haversineDistance raw coordinates is symmetric`() {
        val ab = haversineDistance(40.0, -74.0, 51.5, 0.0)
        val ba = haversineDistance(51.5, 0.0, 40.0, -74.0)
        assertEquals(ab, ba, 0.001)
    }

    // calculateBearing

    @Test
    fun `calculateBearing due north returns 0`() {
        assertEquals(0.0, calculateBearing(0.0, 0.0, 1.0, 0.0), 0.1)
    }

    @Test
    fun `calculateBearing due east returns 90`() {
        assertEquals(90.0, calculateBearing(0.0, 0.0, 0.0, 1.0), 0.1)
    }

    @Test
    fun `calculateBearing due south returns 180`() {
        assertEquals(180.0, calculateBearing(1.0, 0.0, 0.0, 0.0), 0.1)
    }

    @Test
    fun `calculateBearing due west returns 270`() {
        assertEquals(270.0, calculateBearing(0.0, 1.0, 0.0, 0.0), 0.1)
    }

    @Test
    fun `calculateBearing result is in range 0 to 360`() {
        val bearing = calculateBearing(51.5, 0.0, 40.7, -74.0)
        assertTrue("bearing $bearing out of range [0,360)", bearing >= 0.0 && bearing < 360.0)
    }

    // advancePosition

    @Test
    fun `advancePosition north increases latitude`() {
        val (newLat, newLon) = advancePosition(0.0, 0.0, 0.0, 111_195.0)
        assertTrue(newLat > 0.0)
        assertEquals(0.0, newLon, 0.01)
    }

    @Test
    fun `advancePosition east increases longitude`() {
        val (newLat, newLon) = advancePosition(0.0, 0.0, 90.0, 111_195.0)
        assertEquals(0.0, newLat, 0.01)
        assertTrue(newLon > 0.0)
    }

    @Test
    fun `advancePosition zero distance returns same position`() {
        val (newLat, newLon) = advancePosition(51.5, 0.0, 45.0, 0.0)
        assertEquals(51.5, newLat, 0.01)
        assertEquals(0.0, newLon, 0.01)
    }

    @Test
    fun `advancePosition south decreases latitude`() {
        val (newLat, newLon) = advancePosition(1.0, 0.0, 180.0, 111_195.0)
        assertTrue(newLat < 1.0)
    }

    // RDP simplify with collinear points (zero-length line segment)

    @Test
    fun `rdpSimplify two points returns both`() {
        val points = listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0))
        val result = rdpSimplify(points, 0.1)
        assertEquals(2, result.size)
    }

    @Test
    fun `rdpSimplify single point returns it`() {
        val points = listOf(LatLng(0.0, 0.0))
        val result = rdpSimplify(points, 0.1)
        assertEquals(1, result.size)
        assertEquals(points[0], result[0])
    }

    @Test
    fun `rdpSimplify empty list returns empty`() {
        val result = rdpSimplify(emptyList(), 0.1)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `rdpSimplify removes intermediate collinear points`() {
        val points =
            listOf(
                LatLng(0.0, 0.0),
                LatLng(0.5, 0.5),
                LatLng(1.0, 1.0),
            )
        val result = rdpSimplify(points, 10.0)
        assertEquals(2, result.size)
        assertEquals(points.first(), result.first())
        assertEquals(points.last(), result.last())
    }

    @Test
    fun `rdpSimplify keeps points beyond epsilon`() {
        val points =
            listOf(
                LatLng(0.0, 0.0),
                LatLng(0.5, 0.1),
                LatLng(1.0, 0.0),
            )
        val result = rdpSimplify(points, 0.001)
        assertEquals(3, result.size)
    }

    // interpolatePosition

    @Test
    fun `interpolatePosition fraction 0 returns start`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(1.0, 1.0)
        val result = interpolatePosition(from, to, 0.0)
        assertEquals(from, result)
    }

    @Test
    fun `interpolatePosition fraction 1 returns end`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(1.0, 1.0)
        val result = interpolatePosition(from, to, 1.0)
        assertEquals(to, result)
    }

    @Test
    fun `interpolatePosition fraction half returns midpoint`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(2.0, 2.0)
        val result = interpolatePosition(from, to, 0.5)
        assertEquals(1.0, result.latitude, 0.0001)
        assertEquals(1.0, result.longitude, 0.0001)
    }

    @Test
    fun `interpolatePosition negative fraction extrapolates`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(1.0, 0.0)
        val result = interpolatePosition(from, to, -0.5)
        assertTrue(result.latitude < 0.0)
    }

    @Test
    fun `interpolatePosition fraction greater than 1 extrapolates`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(1.0, 0.0)
        val result = interpolatePosition(from, to, 1.5)
        assertTrue(result.latitude > 1.0)
    }

    // snapBearingToCardinal

    @Test
    fun `snapBearingToCardinal with snap false returns unchanged`() {
        assertEquals(37f, snapBearingToCardinal(37f, false), 0.01f)
    }

    @Test
    fun `snapBearingToCardinal snaps 0 to 0`() {
        assertEquals(0f, snapBearingToCardinal(0f, true), 0.01f)
    }

    @Test
    fun `snapBearingToCardinal snaps 90 to 90`() {
        assertEquals(90f, snapBearingToCardinal(90f, true), 0.01f)
    }

    @Test
    fun `snapBearingToCardinal snaps 45 to 45`() {
        assertEquals(45f, snapBearingToCardinal(45f, true), 0.01f)
    }

    @Test
    fun `snapBearingToCardinal snaps 10 to 0`() {
        assertEquals(0f, snapBearingToCardinal(10f, true), 0.01f)
    }

    @Test
    fun `snapBearingToCardinal snaps 80 to 90`() {
        assertEquals(90f, snapBearingToCardinal(80f, true), 0.01f)
    }

    @Test
    fun `snapBearingToCardinal snaps 270 to 270`() {
        assertEquals(270f, snapBearingToCardinal(270f, true), 0.01f)
    }

    @Test
    fun `snapBearingToCardinal snaps 355 to 0`() {
        val result = snapBearingToCardinal(355f, true)
        assertTrue(result == 0f || result == 360f)
    }

    // addGpsJitter

    @Test
    fun `addGpsJitter returns position near original`() {
        val pos = LatLng(0.0, 0.0)
        val jittered = addGpsJitter(pos, 1.0)
        val dist = haversineDistance(pos, jittered)
        assertTrue("jittered position should be within 1m", dist <= 1.5)
    }

    @Test
    fun `addGpsJitter with zero maxJitter returns same position`() {
        val pos = LatLng(0.0, 0.0)
        val jittered = addGpsJitter(pos, 0.0)
        assertEquals(pos, jittered)
    }

    // metersToLatDegrees

    @Test
    fun `metersToLatDegrees 111km is approx 1 degree`() {
        val degrees = metersToLatDegrees(111_195.0)
        assertEquals(1.0, degrees, 0.1)
    }

    @Test
    fun `metersToLatDegrees zero is zero`() {
        assertEquals(0.0, metersToLatDegrees(0.0), 0.0001)
    }

    @Test
    fun `metersToLatDegrees negative meters gives negative degrees`() {
        assertTrue(metersToLatDegrees(-1000.0) < 0.0)
    }

    // metersToLngDegrees

    @Test
    fun `metersToLngDegrees at equator 111km is approx 1 degree`() {
        val degrees = metersToLngDegrees(111_195.0, 0.0)
        assertEquals(1.0, degrees, 0.1)
    }

    @Test
    fun `metersToLngDegrees at higher latitude gives larger degrees`() {
        val atEquator = metersToLngDegrees(1000.0, 0.0)
        val atHighLat = metersToLngDegrees(1000.0, 60.0)
        assertTrue("degrees at 60° lat should be larger", atHighLat > atEquator)
    }

    @Test
    fun `metersToLngDegrees zero is zero`() {
        assertEquals(0.0, metersToLngDegrees(0.0, 0.0), 0.0001)
    }

    // haversineDistance raw overload

    @Test
    fun `haversineDistance raw coords matches LatLng overload`() {
        val a = LatLng(48.8566, 2.3522)
        val b = LatLng(51.5074, -0.1278)
        val viaLatLng = haversineDistance(a, b)
        val viaRaw = haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
        assertEquals(viaLatLng, viaRaw, 0.001)
    }

    // calculateBearing raw overload

    @Test
    fun `calculateBearing raw coords matches LatLng overload`() {
        val a = LatLng(48.8566, 2.3522)
        val b = LatLng(51.5074, -0.1278)
        val viaRaw1 = calculateBearing(a.latitude, a.longitude, b.latitude, b.longitude)
        val viaRaw2 = calculateBearing(a.latitude, a.longitude, b.latitude, b.longitude)
        assertEquals(viaRaw1, viaRaw2, 0.001)
    }

    // parseRawLatLng

    @Test
    fun `parseRawLatLng parses comma separated pair`() {
        val result = parseRawLatLng("35.6762,139.6503")
        assertEquals(LatLng(35.6762, 139.6503), result)
    }

    @Test
    fun `parseRawLatLng parses pair with space after comma`() {
        val result = parseRawLatLng("35.6762, 139.6503")
        assertEquals(LatLng(35.6762, 139.6503), result)
    }

    @Test
    fun `parseRawLatLng parses negative coordinates`() {
        val result = parseRawLatLng("-33.8688, 151.2093")
        assertEquals(LatLng(-33.8688, 151.2093), result)
    }

    @Test
    fun `parseRawLatLng parses boundary values`() {
        assertEquals(LatLng(-90.0, 180.0), parseRawLatLng("-90,180"))
        assertEquals(LatLng(90.0, -180.0), parseRawLatLng("90,-180"))
    }

    @Test
    fun `parseRawLatLng rejects out of range latitude`() {
        assertEquals(null, parseRawLatLng("91,0"))
        assertEquals(null, parseRawLatLng("-91,0"))
    }

    @Test
    fun `parseRawLatLng rejects out of range longitude`() {
        assertEquals(null, parseRawLatLng("0,181"))
        assertEquals(null, parseRawLatLng("0,-181"))
    }

    @Test
    fun `parseRawLatLng rejects plain text`() {
        assertEquals(null, parseRawLatLng("Tokyo"))
    }

    @Test
    fun `parseRawLatLng rejects single number`() {
        assertEquals(null, parseRawLatLng("35.6762"))
    }

    @Test
    fun `parseRawLatLng rejects empty string`() {
        assertEquals(null, parseRawLatLng(""))
    }

    @Test
    fun `parseRawLatLng tolerates surrounding whitespace`() {
        val result = parseRawLatLng("  35.6762 , 139.6503  ")
        assertEquals(LatLng(35.6762, 139.6503), result)
    }
}
