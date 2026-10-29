package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GuidedPathTest {
    @Test
    fun `default shows every guided feature`() {
        assertEquals(listOf(true, true, true), GuidedPath.DEFAULT.visibility())
    }

    @Test
    fun `pokemon go hides only planting`() {
        assertEquals(listOf(false, true, true), GuidedPath.POKEMON_GO.visibility())
    }

    @Test
    fun `pikmin bloom hides tap to walk and reconnect accessory`() {
        assertEquals(listOf(true, false, false), GuidedPath.PIKMIN_BLOOM.visibility())
    }

    @Test
    fun `game package is set only on game paths`() {
        assertEquals(
            listOf(null, "com.nianticlabs.pokemongo", "com.nianticlabs.pikmin"),
            GuidedPath.entries.map { it.gamePackage },
        )
    }

    private fun GuidedPath.visibility() = listOf(showsPlanting, showsTapToWalk, showsReconnectAccessory)
}
