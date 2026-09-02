package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.data.DebugStats
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatBearingTextTest {
    private fun stats(
        bearing: Float,
        hasBearing: Boolean,
    ) = DebugStats(
        latitude = 0.0,
        longitude = 0.0,
        speedMs = 0f,
        altitudeMeters = 0.0,
        accuracyMeters = 5f,
        bearing = bearing,
        hasBearing = hasBearing,
        tickIntervalMs = 1000L,
        jitterRadiusMeters = 0.0,
    )

    @Test
    fun `shows em-dash when bearing unset`() {
        assertEquals("—", formatBearingText(stats(bearing = 0f, hasBearing = false)))
    }

    @Test
    fun `shows numeric bearing when set`() {
        assertEquals("90°", formatBearingText(stats(bearing = 90f, hasBearing = true)))
    }
}
