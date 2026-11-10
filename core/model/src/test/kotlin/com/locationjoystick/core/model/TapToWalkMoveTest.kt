package com.locationjoystick.core.model

import com.locationjoystick.core.model.TapToWalkMove.TELEPORT
import com.locationjoystick.core.model.TapToWalkMove.VIA_ROADS
import com.locationjoystick.core.model.TapToWalkMove.WALK
import org.junit.Assert.assertEquals
import org.junit.Test

class TapToWalkMoveTest {
    @Test
    fun `withMove refuses to uncheck the last move`() {
        assertEquals(setOf(WALK), setOf(WALK).withMove(WALK, false))
    }

    @Test
    fun `withMove adds and removes moves`() {
        assertEquals(setOf(WALK, TELEPORT), setOf(WALK).withMove(TELEPORT, true))
        assertEquals(setOf(TELEPORT), setOf(WALK, TELEPORT).withMove(WALK, false))
    }

    @Test
    fun `effective moves keep enum order`() {
        assertEquals(listOf(WALK, VIA_ROADS, TELEPORT), effectiveTapToWalkMoves(setOf(TELEPORT, VIA_ROADS, WALK), false))
    }

    @Test
    fun `hide teleport drops teleport`() {
        assertEquals(listOf(WALK), effectiveTapToWalkMoves(setOf(WALK, TELEPORT), true))
    }

    @Test
    fun `hide teleport with only teleport stored falls back to walk`() {
        assertEquals(listOf(WALK), effectiveTapToWalkMoves(setOf(TELEPORT), true))
    }
}
