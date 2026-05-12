package com.locationjoystick.feature.joystick.impl

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import android.view.View
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
        private const val JOYSTICK_SIZE_DP = 160
        private const val MOVE_STEP_SECONDS = 0.1
    }

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var mockLocationService: MockLocationService? = null

    private var isLocked = false

    inner class LocalBinder : Binder() {
        fun getService(): JoystickOverlayService = this@JoystickOverlayService
    }

    private val binder = LocalBinder()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
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
        bindService(
            Intent(this, MockLocationService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Service was not bound when attempting to unbind", e)
        }
        super.onDestroy()
    }

    fun setLocked(locked: Boolean) {
        isLocked = locked
        Log.d(TAG, "Joystick locked: $locked")
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun createOverlayView(): View {
        val view = JoystickView(this)

        view.onInputChanged = { input ->
            if (input.force > 0f) {
                serviceScope.launch {
                    applyJoystickInput(input)
                }
            }
        }

        view.shouldResetOnRelease = { !isLocked }

        view.onReleased = {
            Log.d(TAG, "Joystick released — position held")
        }

        makeDraggable(view)
        return view
    }

    override fun getWindowManagerParams() = super.getWindowManagerParams().also { params ->
        val sizePx = (JOYSTICK_SIZE_DP * resources.displayMetrics.density).toInt()
        params.width = sizePx
        params.height = sizePx
    }

    private suspend fun applyJoystickInput(input: JoystickInput) {
        val currentPos = locationRepository.currentPosition.value ?: return
        val speedProfile = settingsRepository.getActiveSpeedProfile().first()
        val speedMs = speedProfile.speedMetersPerSecond

        val angleRad = Math.toRadians(input.angleDegrees.toDouble())
        val distanceMeters = speedMs * MOVE_STEP_SECONDS * input.force

        val dLat = distanceMeters * cos(angleRad) / 111320.0
        val dLon = distanceMeters * sin(angleRad) /
            (111320.0 * cos(Math.toRadians(currentPos.latitude)))

        val nextPos = LatLng(
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

    private fun makeDraggable(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val params = v.layoutParams as? android.view.WindowManager.LayoutParams
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    updateOverlayPosition(initialX + dx, initialY + dy)
                    false
                }
                else -> false
            }
        }
    }
}
