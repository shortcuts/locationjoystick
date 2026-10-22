package com.locationjoystick.feature.widget.impl

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TapToWalkOverlayTest {
    private val windowManager = mockk<WindowManager>(relaxed = true)
    private val overlay =
        TapToWalkOverlay(
            context = ApplicationProvider.getApplicationContext<Context>(),
            windowManager = windowManager,
            lifecycleOwner = mockk<LifecycleOwner>(relaxed = true),
            savedStateRegistryOwner = mockk<SavedStateRegistryOwner>(relaxed = true),
            onWalkTo = {},
            getPosition = { null },
            onDismissed = {},
        )

    @Test
    fun `dismiss right after show removes the window before it attaches`() {
        overlay.show()
        overlay.dismiss()
        verify(exactly = 1) { windowManager.removeView(any<View>()) }
    }

    @Test
    fun `overlay counts as showing before the window attaches`() {
        overlay.show()
        assertTrue(overlay.isShowing())
        overlay.dismiss()
        assertFalse(overlay.isShowing())
    }
}
