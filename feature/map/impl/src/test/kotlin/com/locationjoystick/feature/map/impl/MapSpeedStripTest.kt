package com.locationjoystick.feature.map.impl

import com.locationjoystick.core.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class MapSpeedStripTest {
    @Test
    fun `speedInUnit converts metres per second to km per hour`() {
        assertEquals(3.6, speedInUnit(1.0f, SpeedUnit.KMH), 1e-6)
    }

    @Test
    fun `speedInUnit converts metres per second to miles per hour`() {
        assertEquals(2.236936, speedInUnit(1.0f, SpeedUnit.MPH), 1e-6)
    }

    @Test
    fun `speedInUnit treats null as stopped`() {
        assertEquals(0.0, speedInUnit(null, SpeedUnit.KMH), 0.0)
    }
}
