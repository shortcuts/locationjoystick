package com.locationjoystick.feature.joystick.impl

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.overlay.OverlayService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

@AndroidEntryPoint
class JoystickOverlayService : OverlayService() {
    companion object {
        private const val TAG = "JoystickOverlayService"
        private const val JOYSTICK_SIZE_DP = 90
        private const val MOVE_STEP_SECONDS = 0.1
    }

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var mockLocationService: MockLocationService? = null

    var locked = false

    inner class LocalBinder : Binder() {
        fun getService(): JoystickOverlayService = this@JoystickOverlayService
    }

    private val binder = LocalBinder()

    private val overlayVisibilityReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_OVERLAY_HIDE -> hideOverlay()
                    ACTION_OVERLAY_SHOW -> showOverlay()
                }
            }
        }

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                mockLocationService = (binder as MockLocationService.LocalBinder).getService()
                Log.d(TAG, "Bound to MockLocationService")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                mockLocationService = null
                Log.d(TAG, "Unbound from MockLocationService")
            }
        }

    override fun onCreate() {
        super.onCreate()
        val filter =
            IntentFilter().apply {
                addAction(ACTION_OVERLAY_HIDE)
                addAction(ACTION_OVERLAY_SHOW)
            }
        ContextCompat.registerReceiver(this, overlayVisibilityReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        bindService(
            Intent(this, MockLocationService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            unregisterReceiver(overlayVisibilityReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Overlay visibility receiver not registered", e)
        }
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Service was not bound when attempting to unbind", e)
        }
        super.onDestroy()
    }

    val isOverlayVisible: Boolean
        get() = overlayView?.isAttachedToWindow == true

    fun setIsLocked(value: Boolean) {
        locked = value
        Log.d(TAG, "Joystick locked: $value")
    }

    fun toggleOverlay() {
        // Simple toggle: if view is attached, hide it; otherwise show it
        val view = overlayView
        if (view != null && view.isAttachedToWindow) {
            hideOverlay()
        } else {
            showOverlay()
        }
    }

    override fun createOverlayView(): View {
        val view = JoystickView(this)

        view.onInputChanged = { input ->
            if (input.force > 0f) {
                serviceScope.launch {
                    applyJoystickInput(input)
                }
            }
        }

        view.shouldResetOnRelease = { !locked }

        view.onReleased = {
            Log.d(TAG, "Joystick released — position held")
        }

        var dragInitialX = 0
        var dragInitialY = 0
        var dragInitialRawX = 0f
        var dragInitialRawY = 0f

        view.onDragHandleDown = { rawX, rawY ->
            val params = overlayView?.layoutParams as? WindowManager.LayoutParams
            dragInitialX = params?.x ?: 0
            dragInitialY = params?.y ?: 0
            dragInitialRawX = rawX
            dragInitialRawY = rawY
        }

        view.onDragHandleMoved = { rawX, rawY ->
            val dx = (rawX - dragInitialRawX).toInt()
            val dy = (rawY - dragInitialRawY).toInt()
            updateOverlayPosition(dragInitialX + dx, dragInitialY + dy)
        }

        return view
    }

    override fun getWindowManagerParams(view: View): WindowManager.LayoutParams {
        val sizePx = (JOYSTICK_SIZE_DP * resources.displayMetrics.density).toInt()

        return WindowManager
            .LayoutParams(
                sizePx,
                sizePx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                x = 0
                y = 0
            }
    }

    private suspend fun applyJoystickInput(input: JoystickInput) {
        val currentPos = locationRepository.currentPosition.value ?: return
        val speedProfile = settingsRepository.getActiveSpeedProfile().first()
        val speedMs = speedProfile.speedMetersPerSecond

        val angleRad = Math.toRadians(input.angleDegrees.toDouble())
        val distanceMeters = speedMs * MOVE_STEP_SECONDS * input.force

        val dLat = distanceMeters * cos(angleRad) / 111320.0
        val dLon =
            distanceMeters * sin(angleRad) /
                (111320.0 * cos(Math.toRadians(currentPos.latitude)))

        val nextPos =
            LatLng(
                latitude = currentPos.latitude + dLat,
                longitude = currentPos.longitude + dLon,
            )

        locationRepository.updatePosition(nextPos)

        mockLocationService?.updatePositionWithVector(
            lat = nextPos.latitude,
            lon = nextPos.longitude,
            speedMs = (speedMs * input.force).toFloat(),
            bearing = input.angleDegrees,
        )
    }
}
