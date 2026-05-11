package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatLngTest {

    // distanceTo

    @Test
    fun `distanceTo same point returns zero`() {
        val point = LatLng(48.8566, 2.3522)
        assertEquals(0.0, point.distanceTo(point), 0.001)
    }

    @Test
    fun `distanceTo 1 degree latitude is approx 111km`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(1.0, 0.0)
        assertEquals(111_195.0, a.distanceTo(b), 500.0)
    }

    @Test
    fun `distanceTo 1 degree longitude at equator is approx 111km`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.0, 1.0)
        assertEquals(111_195.0, a.distanceTo(b), 500.0)
    }

    @Test
    fun `distanceTo is symmetric`() {
        val a = LatLng(51.5074, -0.1278)
        val b = LatLng(48.8566, 2.3522)
        assertEquals(a.distanceTo(b), b.distanceTo(a), 0.001)
    }

    @Test
    fun `distanceTo London to Paris is approx 340km`() {
        val london = LatLng(51.5074, -0.1278)
        val paris = LatLng(48.8566, 2.3522)
        assertEquals(340_000.0, london.distanceTo(paris), 5_000.0)
    }

    @Test
    fun `distanceTo short distance 100m is accurate`() {
        // 100m north: 0.0009° latitude ≈ 100m
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.0009, 0.0)
        assertEquals(100.0, a.distanceTo(b), 2.0)
    }

    // bearingTo

    @Test
    fun `bearingTo due north returns 0`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(1.0, 0.0)
        assertEquals(0.0, a.bearingTo(b), 0.1)
    }

    @Test
    fun `bearingTo due east returns 90`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.0, 1.0)
        assertEquals(90.0, a.bearingTo(b), 0.1)
    }

    @Test
    fun `bearingTo due south returns 180`() {
        val a = LatLng(1.0, 0.0)
        val b = LatLng(0.0, 0.0)
        assertEquals(180.0, a.bearingTo(b), 0.1)
    }

    @Test
    fun `bearingTo due west returns 270`() {
        val a = LatLng(0.0, 1.0)
        val b = LatLng(0.0, 0.0)
        assertEquals(270.0, a.bearingTo(b), 0.1)
    }

    @Test
    fun `bearingTo result is in range 0 to 360`() {
        val a = LatLng(51.5, 0.0)
        val b = LatLng(40.7, -74.0)
        val bearing = a.bearingTo(b)
        assertTrue("bearing $bearing out of range [0,360)", bearing >= 0.0 && bearing < 360.0)
    }

    @Test
    fun `bearingTo northeast is between 0 and 90`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(1.0, 1.0)
        val bearing = a.bearingTo(b)
        assertTrue("expected NE bearing, got $bearing", bearing > 0.0 && bearing < 90.0)
    }
}
