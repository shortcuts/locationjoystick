package com.locationjoystick.core.common.util

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BearingTrackerTest {
    @Test
    fun `first advance call returns null`() {
        val tracker = BearingTracker()
        assertNull(tracker.advance(LatLng(0.0, 0.0)))
    }

    @Test
    fun `advance with distinct positions returns bearing between them`() {
        val tracker = BearingTracker()
        val p0 = LatLng(0.0, 0.0)
        val p1 = LatLng(1.0, 1.0)
        tracker.advance(p0)

        val bearing = tracker.advance(p1)

        val expected = calculateBearing(p0.latitude, p0.longitude, p1.latitude, p1.longitude).toFloat()
        assertEquals(expected, bearing)
    }

    @Test
    fun `advance with same position twice returns null on second call`() {
        val tracker = BearingTracker()
        val p = LatLng(1.0, 1.0)
        tracker.advance(p)

        assertNull(tracker.advance(p))
    }
}
