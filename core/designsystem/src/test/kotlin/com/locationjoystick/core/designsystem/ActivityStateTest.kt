package com.locationjoystick.core.designsystem

import androidx.compose.material3.darkColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityStateTest {
    private val scheme = darkColorScheme()

    @Test
    fun `state is idle, moving or paused`() {
        assertEquals(ActivityState.IDLE, activityState(active = false, paused = false))
        assertEquals(ActivityState.IDLE, activityState(active = false, paused = true))
        assertEquals(ActivityState.MOVING, activityState(active = true, paused = false))
        assertEquals(ActivityState.PAUSED, activityState(active = true, paused = true))
    }

    @Test
    fun `idle is neutral, moving green, paused primary`() {
        assertEquals(scheme.surfaceVariant, ActivityState.IDLE.containerColor(scheme))
        assertEquals(scheme.onSurfaceVariant, ActivityState.IDLE.contentColor(scheme))
        assertEquals(LjSuccess, ActivityState.MOVING.containerColor(scheme))
        assertEquals(LjBg, ActivityState.MOVING.contentColor(scheme))
        assertEquals(scheme.primary, ActivityState.PAUSED.containerColor(scheme))
        assertEquals(LjBg, ActivityState.PAUSED.contentColor(scheme))
    }
}
