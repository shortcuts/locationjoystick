package com.locationjoystick.feature.joystick.impl

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.overlay.OverlayService
import com.locationjoystick.core.overlay.OverlayServiceHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
        private const val MOVE_STEP_MS = (MOVE_STEP_SECONDS * 1000).toLong()
    }

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "JoystickOverlayService coroutine crashed", throwable)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private val overlayHelper = OverlayServiceHelper(TAG)

    private var mockLocationService: MockLocationService? = null

    var locked = false

    /** Last joystick input received; used to keep moving when locked and finger is lifted. */
    private var lastInput: JoystickInput? = null

    /** Running only when locked and finger is off screen — drives continuous movement. */
    private var lockedMovementJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): JoystickOverlayService = this@JoystickOverlayService
    }

    private val binder = LocalBinder()

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
        overlayHelper.registerOverlayVisibilityReceiver(this, this)
        overlayHelper.bindTrackedService(this, Intent(this, MockLocationService::class.java), serviceConnection)
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        stopLockedMovement()
        serviceScope.cancel()
        overlayHelper.cleanupOverlayBindings(this)
        super.onDestroy()
    }

    val isOverlayVisible: Boolean
        get() = overlayView?.isAttachedToWindow == true

    fun setIsLocked(value: Boolean) {
        locked = value
        (overlayView as? JoystickView)?.isLocked = value
        if (!value) {
            stopLockedMovement()
        }
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

        view.isLocked = locked

        view.onInputChanged = { input ->
            lastInput = input
            // While finger is on screen, apply each input directly.
            // Also stop any running locked-movement loop (finger took back control).
            stopLockedMovement()
            if (input.force > 0f) {
                serviceScope.launch {
                    applyJoystickInput(input)
                }
            }
        }

        view.shouldResetOnRelease = { !view.isLocked }

        view.onReleased = {
            if (view.isLocked) {
                val input = lastInput
                if (input != null && input.force > 0f) {
                    startLockedMovement(input)
                    Log.d(TAG, "Joystick released (locked) — continuing movement at angle=${input.angleDegrees}, force=${input.force}")
                } else {
                    Log.d(TAG, "Joystick released (locked) — no active input, holding position")
                }
            } else {
                Log.d(TAG, "Joystick released — position held")
            }
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

    private fun startLockedMovement(input: JoystickInput) {
        stopLockedMovement()
        lockedMovementJob =
            serviceScope.launch {
                while (true) {
                    delay(MOVE_STEP_MS)
                    applyJoystickInput(input)
                }
            }
    }

    private fun stopLockedMovement() {
        lockedMovementJob?.cancel()
        lockedMovementJob = null
    }

    private suspend fun applyJoystickInput(input: JoystickInput) {
        val currentPos = locationRepository.currentPosition.value ?: return
        val speedProfile = settingsRepository.getActiveSpeedProfile().first()
        val speedMs = speedProfile.speedMetersPerSecond

        // Convert screen math angle (0=east, CCW, screen-Y inverted) to geographic bearing (0=north, CW).
        // Derivation: bearing = atan2(cos(α), -sin(α)) where α is the screen math angle.
        val angleRad = Math.toRadians(input.angleDegrees.toDouble())
        val bearingRad = Math.atan2(cos(angleRad), -sin(angleRad))
        val bearingDeg = ((Math.toDegrees(bearingRad) + 360.0) % 360.0)
        val distanceMeters = speedMs * MOVE_STEP_SECONDS * input.force

        val dLat = distanceMeters * cos(bearingRad) / 111320.0
        val dLon =
            distanceMeters * sin(bearingRad) /
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
            bearing = bearingDeg.toFloat(),
        )
    }
}
