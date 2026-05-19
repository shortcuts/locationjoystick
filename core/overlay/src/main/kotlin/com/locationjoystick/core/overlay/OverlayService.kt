package com.locationjoystick.core.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.locationjoystick.core.common.constants.AppConstants

abstract class OverlayService : Service() {
    private val tag: String get() = this::class.java.simpleName

    companion object {
        val ACTION_OVERLAY_HIDE = AppConstants.ServiceConstants.ACTION_OVERLAY_HIDE
        val ACTION_OVERLAY_SHOW = AppConstants.ServiceConstants.ACTION_OVERLAY_SHOW
    }

    protected lateinit var windowManager: WindowManager
        private set

    protected var overlayView: View? = null

    private var currentParams: WindowManager.LayoutParams? = null

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

    protected fun updateOverlayPosition(
        x: Int,
        y: Int,
    ) {
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
