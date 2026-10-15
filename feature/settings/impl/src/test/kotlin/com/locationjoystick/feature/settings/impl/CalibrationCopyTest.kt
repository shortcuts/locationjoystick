package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.location.CalibrationFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationCopyTest {
    @Test
    fun `every failure has its own non-blank message`() {
        val ids = CalibrationFailure.entries.map { calibrationFailureMessageRes(it) }
        assertEquals(10, ids.size)
        assertEquals("distinct ids", ids.size, ids.toSet().size)
        assertTrue(ids.all { it != 0 })
    }
}
