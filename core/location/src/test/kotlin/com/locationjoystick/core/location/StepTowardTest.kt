package com.locationjoystick.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class StepTowardTest {
    @Test
    fun `reaches target exactly when within maxStep`() {
        assertEquals(10.0, stepToward(current = 9.7, target = 10.0, maxStep = 0.5), 0.0)
    }

    @Test
    fun `moves by exactly maxStep toward target when farther`() {
        assertEquals(9.5, stepToward(current = 9.0, target = 20.0, maxStep = 0.5), 0.0)
    }

    @Test
    fun `moves in the negative direction symmetrically`() {
        assertEquals(9.5, stepToward(current = 10.0, target = 0.0, maxStep = 0.5), 0.0)
    }

    @Test
    fun `no-op when current equals target`() {
        assertEquals(5.0, stepToward(current = 5.0, target = 5.0, maxStep = 0.5), 0.0)
    }
}
