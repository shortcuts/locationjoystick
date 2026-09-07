package com.locationjoystick.feature.settings.impl

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.locationjoystick.core.designsystem.LjTheme

/**
 * Full-screen system overlay for compass/scale calibration — drawn on top of whatever app is in
 * the foreground (a game left running underneath) so the user calibrates against the real thing
 * instead of a screenshot mockup inside Settings. Mirrors TapToWalkOverlay's WindowManager pattern
 * (`:feature:widget:impl`), duplicated here rather than shared since Settings has no existing
 * dependency on that module and the class is ~30 lines.
 */
internal class CalibrationOverlay(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null

    fun show() {
        if (view != null) return
        val composeView =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
                setContent { LjTheme { content { dismiss() } } }
            }
        try {
            windowManager.addView(composeView, layoutParams())
            view = composeView
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show calibration overlay", e)
        }
    }

    fun dismiss() {
        view?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dismiss calibration overlay", e)
            }
        }
        view = null
    }

    private fun layoutParams() =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // No FLAG_NOT_TOUCH_MODAL — intercepts all taps, same as TapToWalkOverlay.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )

    companion object {
        private const val TAG = "CalibrationOverlay"
    }
}
