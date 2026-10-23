package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitMonitorTest {
    @Test
    fun unchangedResidualsNeverAlarmOverFiftyLegs() {
        val legs = List(50) { leg -> List(15) { i -> 2.0 + 0.4 * kotlin.math.sin((leg * 15 + i).toDouble()) } }
        for (l in legs) assertNull(FitMonitor.alarmIndex(l, 2.0))
    }

    @Test
    fun threeAndHalfTimesResidualAlarmsOnFourthMove() {
        // excess per move 3.5 - 1 - 0.25 = 2.25 under h = 8: crosses on the fourth move; 12x on the first alone passes
        assertEquals(3, FitMonitor.alarmIndex(List(10) { 7.0 }, 2.0))
        assertEquals(0, FitMonitor.alarmIndex(listOf(24.0, 24.0), 2.0))
    }

    @Test
    fun movesWithoutUsableBandsAreSkipped() {
        assertEquals(1, FitMonitor.alarmIndex(listOf(null, 24.0), 2.0))
    }

    @Test
    fun legWithNoUsableMovesIsNotAMatch() {
        assertEquals(0, FitMonitor.alarmIndex(listOf(null, null, null), 2.0))
    }

    @Test
    fun emptyLegIsNotAlarmed() {
        assertNull(FitMonitor.alarmIndex(emptyList(), 2.0))
    }

    @Test
    fun oneNullAmongValidMovesBehavesAsToday() {
        assertNull(FitMonitor.alarmIndex(listOf(2.0, null, 2.0), 2.0))
    }

    @Test
    fun nonPositiveOrNaNBaselineIsFlooredNotThrown() {
        for (base in listOf(0.0, -1.0, Double.NaN)) {
            assertNull(FitMonitor.alarmIndex(listOf(2.0, 2.0), base))
            assertEquals(3, FitMonitor.alarmIndex(List(10) { 7.0 }, base))
        }
    }
}
