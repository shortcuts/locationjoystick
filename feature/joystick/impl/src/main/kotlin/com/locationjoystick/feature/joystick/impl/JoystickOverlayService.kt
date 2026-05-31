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
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.SpeedProfile
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

private fun Double.toRadians(): Double = Math.toRadians(this)

private fun Double.toDegrees(): Double = Math.toDegrees(this)

private const val TAG = "JoystickOverlayService"

/**
 * Converts a screen angle (0=east, CCW positive) to a geographic bearing in degrees (0=north, CW positive).
 */
internal fun angleToBearing(angleDegrees: Float): Double {
    val angleRad = angleDegrees.toDouble().toRadians()
    val bearingRad = kotlin.math.atan2(cos(angleRad), -sin(angleRad))
    return (bearingRad.toDegrees() + 360.0) % 360.0
}

/**
 * Pure position-advance function for one joystick tick.
 * Uses flat-earth approximation (accurate to within ~0.1% for distances < 1 km).
 */
internal fun computeJoystickStep(
    currentPos: LatLng,
    angleDegrees: Float,
    force: Float,
    speedMs: Double,
    stepSeconds: Double,
): LatLng {
    val bearingDeg = angleToBearing(angleDegrees)
    val bearingRad = bearingDeg.toRadians()
    val distanceMeters = speedMs * stepSeconds * force
    val dLat = distanceMeters * cos(bearingRad) / AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
    val dLon =
        distanceMeters * sin(bearingRad) /
            (AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE * cos(currentPos.latitude.toRadians()))
    return LatLng(latitude = currentPos.latitude + dLat, longitude = currentPos.longitude + dLon)
}

@AndroidEntryPoint
class JoystickOverlayService : OverlayService() {
    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "JoystickOverlayService coroutine crashed", throwable)
        }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private val overlayHelper = OverlayServiceHelper(TAG)

    private var mockLocationService: MockLocationService? = null

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    /** Latest joystick direction+force from touch; read by the movement tick loop. */
    private var lastInput: JoystickInput? = null

    /** Cached active speed profile; updated reactively so ticks never hit DataStore. */
    private val _cachedProfile = MutableStateFlow<SpeedProfile?>(null)
    val cachedProfile: StateFlow<SpeedProfile?> = _cachedProfile.asStateFlow()

    /** Single movement job used for both touch-active and locked-release motion. */
    private var movementJob: Job? = null

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
        val bound = overlayHelper.bindTrackedService(this, Intent(this, MockLocationService::class.java), serviceConnection)
        if (!bound) Log.e(TAG, "Failed to bind MockLocationService — position vector updates will be skipped")
        serviceScope.launch {
            settingsRepository.getActiveSpeedProfile().collect { profile ->
                _cachedProfile.value = profile
            }
        }
    }

    /** Joystick starts hidden; user enables it via the floating widget. */
    override fun showOverlayOnStart(): Boolean = false

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.getBooleanExtra(AppConstants.ServiceConstants.EXTRA_SHOW_OVERLAY, false) == true) {
            showOverlay()
        }
        return result
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        movementJob?.cancel()
        movementJob = null
        serviceScope.cancel()
        overlayHelper.cleanupOverlayBindings(this)
        super.onDestroy()
    }

    val isOverlayVisible: Boolean
        get() = overlayView?.isAttachedToWindow == true

    fun setIsLocked(value: Boolean) {
        _isLocked.value = value
        (overlayView as? JoystickView)?.isLocked = value
        if (!value) {
            movementJob?.cancel()
            movementJob = null
        }
        Log.d(TAG, "Joystick locked: $value")
    }

    fun toggleOverlay() {
        val view = overlayView
        if (view != null && view.isAttachedToWindow) {
            hideOverlay()
            _isVisible.value = false
        } else {
            showOverlay()
            _isVisible.value = true
        }
    }

    override fun createOverlayView(): View {
        val view = JoystickView(this)

        view.isLocked = _isLocked.value

        view.onInputChanged = { input ->
            lastInput = input
            if (input.force > 0f && (movementJob == null || !movementJob!!.isActive)) {
                startMovement()
            }
        }

        view.shouldResetOnRelease = { !view.isLocked }

        view.onReleased = {
            if (view.isLocked) {
                // Locked mode: job keeps running with lastInput direction (no zero-force emitted
                // by JoystickView when shouldResetOnRelease returns false).
                Log.d(TAG, "Joystick released (locked) — continuing movement")
            } else {
                movementJob?.cancel()
                movementJob = null
                locationRepository.setMockMode(MockMode.TELEPORT)
                Log.d(TAG, "Joystick released — movement stopped")
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
        val sizePx = (AppConstants.JoystickConstants.SIZE_DP * resources.displayMetrics.density).toInt()

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

    private fun startMovement() {
        movementJob?.cancel()
        movementJob =
            serviceScope.launch {
                while (isActive) {
                    delay(AppConstants.JoystickConstants.STEP_MS)
                    val input = lastInput ?: continue
                    if (input.force > 0f) {
                        applyJoystickInput(input)
                    }
                }
            }
    }

    private suspend fun applyJoystickInput(input: JoystickInput) {
        val currentPos = locationRepository.currentPosition.value ?: return
        val speedMs = _cachedProfile.value?.speedMetersPerSecond ?: return
        locationRepository.setMockMode(MockMode.JOYSTICK)

        val bearingDeg = angleToBearing(input.angleDegrees)
        val nextPos = computeJoystickStep(currentPos, input.angleDegrees, input.force, speedMs, AppConstants.JoystickConstants.STEP_SECONDS)

        locationRepository.updatePosition(nextPos)

        mockLocationService?.updatePositionWithVector(
            lat = nextPos.latitude,
            lon = nextPos.longitude,
            speedMs = (speedMs * input.force).toFloat(),
            bearing = bearingDeg.toFloat(),
        )
    }
}
