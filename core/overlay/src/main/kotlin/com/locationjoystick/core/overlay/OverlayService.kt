package com.locationjoystick.core.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.LocaleContextWrapper

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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleContextWrapper.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        ensureOverlayCreated()
        if (showOverlayOnStart()) {
            showOverlay()
        } else {
            Log.d(tag, "Overlay view created but not shown (showOverlayOnStart=false)")
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
        val (w, h) = screenSize()
        return Pair(
            x.coerceIn(0, (w - view.width).coerceAtLeast(0)),
            y.coerceIn(0, (h - view.height).coerceAtLeast(0)),
        )
    }

    // WindowManager#currentWindowMetrics requires API 30 — fall back to the deprecated
    // real-metrics query below that, same values.
    @Suppress("DEPRECATION")
    protected fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
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

    /**
     * Creates the overlay view if this service was bound without [onStartCommand]
     * (BIND_AUTO_CREATE). Joystick starts hidden and is shown later from the widget eye/lock.
     */
    private fun ensureOverlayCreated() {
        if (overlayView != null) return
        try {
            val view = createOverlayView()
            val params = getWindowManagerParams(view)
            overlayView = view
            currentParams = params
            Log.d(tag, "Overlay view created")
        } catch (e: Exception) {
            overlayView = null
            currentParams = null
            Log.e(tag, "Failed to create overlay view", e)
        }
    }

    fun showOverlay() {
        ensureOverlayCreated()
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
