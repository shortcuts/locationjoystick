package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitMonitorTest {
    @Test
    fun unchangedResidualsNeverAlarmOverFiftyLegs() {
        val legs = List(50) { leg -> List(15) { i -> 1.0 + 0.2 * kotlin.math.sin((leg * 15 + i).toDouble()) } }
        for (l in legs) assertNull(FitMonitor.alarmIndex(l, 1.0))
    }

    @Test
    fun threeAndHalfTimesResidualAlarmsOnFourthMove() {
        // excess per move 3.5 - 1 - 0.25 = 2.25 under h = 8: crosses on the fourth move; 12x on the first alone passes
        assertEquals(3, FitMonitor.alarmIndex(List(10) { 3.5 }, 1.0))
        assertEquals(0, FitMonitor.alarmIndex(listOf(12.0, 12.0), 1.0))
    }

    @Test
    fun movesWithoutUsableBandsAreSkipped() {
        assertEquals(1, FitMonitor.alarmIndex(listOf(null, 12.0), 1.0))
    }

    @Test
    fun legWithNoUsableMovesIsNotAMatch() {
        assertEquals(0, FitMonitor.alarmIndex(listOf(null, null, null), 1.0))
    }

    @Test
    fun emptyLegIsNotAlarmed() {
        assertNull(FitMonitor.alarmIndex(emptyList(), 1.0))
    }

    @Test
    fun oneNullAmongValidMovesBehavesAsToday() {
        assertNull(FitMonitor.alarmIndex(listOf(1.0, null, 1.0), 1.0))
    }
}
