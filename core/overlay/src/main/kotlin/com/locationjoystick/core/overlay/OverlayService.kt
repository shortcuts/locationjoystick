package com.locationjoystick.core.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

abstract class OverlayService : Service() {

    private val tag: String get() = this::class.java.simpleName

    protected lateinit var windowManager: WindowManager
        private set

    protected var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) {
            val view = createOverlayView()
            val params = getWindowManagerParams()
            try {
                windowManager.addView(view, params)
                overlayView = view
                Log.d(tag, "Overlay view added to WindowManager")
            } catch (e: Exception) {
                Log.e(tag, "Failed to add overlay view to WindowManager", e)
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

    open fun getWindowManagerParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 32
            y = 32
        }

    protected fun updateOverlayPosition(x: Int, y: Int) {
        val view = overlayView ?: return
        try {
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return
            params.x = x
            params.y = y
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update overlay position", e)
        }
    }

    fun showOverlay() {
        val view = overlayView ?: return
        try {
            if (!view.isAttachedToWindow) {
                val params = getWindowManagerParams()
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
                windowManager.removeView(view)
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
                windowManager.removeView(view)
                Log.d(tag, "Overlay view removed from WindowManager")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to remove overlay view from WindowManager", e)
        } finally {
            overlayView = null
        }
    }
}
