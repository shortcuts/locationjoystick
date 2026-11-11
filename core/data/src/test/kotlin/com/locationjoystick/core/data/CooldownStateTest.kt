package com.locationjoystick.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CooldownStateTest {
    @Test
    fun `cooling maps to remaining seconds and distance`() =
        assertEquals(30L to 5000.0, CooldownState.Cooling(30, 60, 5000.0).advisoryPair())

    @Test
    fun `ready maps to null`() = assertNull(CooldownState.Ready.advisoryPair())
}
