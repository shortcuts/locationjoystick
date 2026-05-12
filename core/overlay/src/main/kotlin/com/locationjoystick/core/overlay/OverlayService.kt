package com.locationjoystick.core.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

abstract class OverlayService : Service() {

    private val tag: String get() = this::class.java.simpleName

    protected lateinit var windowManager: WindowManager
        private set

    protected var overlayView: View? = null

    private var currentParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) {
            val view = createOverlayView()

            view.post {
                try {
                    val params = getWindowManagerParams(view)
                    windowManager.addView(view, params)

                    overlayView = view
                    currentParams = params

                    Log.d(tag, "Overlay view added to WindowManager")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to add overlay view to WindowManager", e)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlayView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    abstract fun createOverlayView(): View

    open fun getWindowManagerParams(view: View): WindowManager.LayoutParams {
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds

        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars()
        )

        val usableHeight = bounds.height() - insets.top - insets.bottom

        val centerY = insets.top + ((usableHeight / 2) - (view.measuredHeight / 2))

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = centerY
        }
    }

    protected fun updateOverlayPosition(x: Int, y: Int) {
        val view = overlayView ?: return
        val params = currentParams ?: return

        try {
            params.x = x
            params.y = y

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

