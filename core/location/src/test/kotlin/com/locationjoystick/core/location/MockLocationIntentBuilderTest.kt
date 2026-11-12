package com.locationjoystick.core.location

import android.content.Context
import android.content.Intent
import com.locationjoystick.core.model.RouteStartConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MockLocationIntentBuilderTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun startRouteReplay_carriesLoopWalksBack() {
        val intent = MockLocationIntentBuilder.startRouteReplay(context, "r", 1.4, RouteStartConfig(isLooping = true, loopWalksBack = true))

        assertTrue(intent.toRouteStartConfig().loopWalksBack)
    }

    @Test
    fun toRouteStartConfig_missingExtra_restarts() {
        assertFalse(Intent().toRouteStartConfig().loopWalksBack)
    }
}
