package com.locationjoystick.core.overlay

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.locationjoystick.core.common.constants.AppConstants

/**
 * Base service for creating and managing floating overlay views on top of all apps.
 *
 * Uses TYPE_APPLICATION_OVERLAY which requires SYSTEM_ALERT_WINDOW permission.
 * Subclasses implement [createOverlayView] to provide their specific UI.
 *
 * Key lifecycle:
 * - onCreate: initializes WindowManager
 * - onStartCommand: creates and adds overlay view
 * - onDestroy: removes overlay view to prevent leaks
 *
 * @see JoystickOverlayService
 * @see FloatingWidgetService
 */
abstract class OverlayService : Service() {
    private val tag: String get() = this::class.java.simpleName

    companion object {
        val ACTION_OVERLAY_HIDE = AppConstants.ServiceConstants.ACTION_OVERLAY_HIDE
        val ACTION_OVERLAY_SHOW = AppConstants.ServiceConstants.ACTION_OVERLAY_SHOW
    }

    protected lateinit var windowManager: WindowManager
        private set

    protected var overlayView: View? = null

    protected var currentParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (overlayView == null) {
            val view = createOverlayView()
            try {
                val params = getWindowManagerParams(view)
                overlayView = view
                currentParams = params
                if (showOverlayOnStart()) {
                    windowManager.addView(view, params)
                    Log.d(tag, "Overlay view added to WindowManager")
                } else {
                    Log.d(tag, "Overlay view created but not shown (showOverlayOnStart=false)")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to add overlay view to WindowManager", e)
            }
        }

        return START_STICKY
    }

    /**
     * Override to return false if the overlay should start hidden.
     * Callers can later use [showOverlay] / [toggleOverlay] to show it.
     */
    protected open fun showOverlayOnStart(): Boolean = true

    override fun onDestroy() {
        removeOverlayView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val params = currentParams ?: return
        val view = overlayView ?: return
        if (!view.isAttachedToWindow) return
        // Re-clamp to the new screen dimensions so the overlay never ends up off-screen after rotation.
        updateOverlayPosition(params.x, params.y)
    }

    abstract fun createOverlayView(): View

    open fun getWindowManagerParams(view: View): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                x = 0
                y = 0
            }

    private fun clampPosition(
        x: Int,
        y: Int,
        view: View,
    ): Pair<Int, Int> {
        val metrics = windowManager.currentWindowMetrics
        val w = metrics.bounds.width()
        val h = metrics.bounds.height()
        return Pair(
            x.coerceIn(0, (w - view.width).coerceAtLeast(0)),
            y.coerceIn(0, (h - view.height).coerceAtLeast(0)),
        )
    }

    protected fun updateOverlayPosition(
        x: Int,
        y: Int,
    ) {
        val view = overlayView ?: return
        val params = currentParams ?: return
        try {
            val (cx, cy) = clampPosition(x, y, view)
            params.x = cx
            params.y = cy
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update overlay position", e)
        }
    }

    fun showOverlay() {
        val view = overlayView ?: return
        val params = currentParams ?: return

        try {
            if (!view.isAttachedToWindow) {
                windowManager.addView(view, params)
                Log.d(tag, "Overlay view shown")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to show overlay view", e)
        }
    }

    fun hideOverlay() {
        val view = overlayView ?: return

        try {
            if (view.isAttachedToWindow) {
                windowManager.removeViewImmediate(view)
                Log.d(tag, "Overlay view hidden")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to hide overlay view", e)
        }
    }

    private fun removeOverlayView() {
        val view = overlayView ?: return

        try {
            if (view.isAttachedToWindow) {
                windowManager.removeViewImmediate(view)
                Log.d(tag, "Overlay view removed from WindowManager")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to remove overlay view from WindowManager", e)
        } finally {
            overlayView = null
            currentParams = null
        }
    }
}
