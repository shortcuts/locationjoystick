package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.model.ReconnectRunResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectStatusLabelTest {
    @Test
    fun `every result maps to a distinct label`() {
        val labels = ReconnectRunResult.entries.map(::reconnectStatusLabelRes)
        assertEquals(ReconnectRunResult.entries.size, labels.toSet().size)
    }
}
