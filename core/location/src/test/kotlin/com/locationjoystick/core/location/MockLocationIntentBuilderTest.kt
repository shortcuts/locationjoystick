package com.locationjoystick.core.location

import android.content.Context
import android.content.Intent
import com.locationjoystick.core.model.LoopMode
import com.locationjoystick.core.model.RouteStartConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MockLocationIntentBuilderTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun startRouteReplay_carriesLoopMode() {
        LoopMode.entries.forEach { mode ->
            val intent = MockLocationIntentBuilder.startRouteReplay(context, "r", 1.4, RouteStartConfig(loopMode = mode))

            assertEquals(mode, intent.toRouteStartConfig().loopMode)
        }
    }

    @Test
    fun toRouteStartConfig_missingExtra_isOff() {
        assertEquals(LoopMode.OFF, Intent().toRouteStartConfig().loopMode)
    }
}
