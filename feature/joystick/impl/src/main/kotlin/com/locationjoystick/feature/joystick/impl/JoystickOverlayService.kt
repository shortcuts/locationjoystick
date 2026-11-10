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
import com.locationjoystick.core.common.util.advancePosition
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.shouldIgnoreJoystickInput
import com.locationjoystick.core.model.shouldPreserveEngineMode
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 */
internal fun computeJoystickStep(
    currentPos: LatLng,
    angleDegrees: Float,
    force: Float,
    speedMs: Double,
    stepSeconds: Double,
): LatLng {
    val bearingDeg = angleToBearing(angleDegrees)
    val distanceMeters = speedMs * stepSeconds * force
    val (lat, lon) = advancePosition(currentPos.latitude, currentPos.longitude, bearingDeg, distanceMeters)
    return LatLng(latitude = lat, longitude = lon)
}

@AndroidEntryPoint
class JoystickOverlayService : OverlayService() {
    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var mapController: MapController

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

    private val state = MutableStateFlow<JoystickInputState>(JoystickInputState.Idle)
    internal val inputState: JoystickInputState get() = state.value

    /** Cached active speed profile; updated reactively so ticks never hit DataStore. */
    private val _cachedProfile = MutableStateFlow<SpeedProfile?>(null)
    val cachedProfile: StateFlow<SpeedProfile?> = _cachedProfile.asStateFlow()

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
        serviceScope.launch {
            locationRepository.movement
                .map(::shouldIgnoreJoystickInput)
                .distinctUntilChanged()
                .collect { ignored ->
                    withContext(Dispatchers.Main) {
                        (overlayView as? JoystickView)?.inputIgnored = ignored
                    }
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
            showJoystick()
        }
        return result
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        state.value.job?.cancel()
        state.value = JoystickInputState.Idle
        serviceScope.cancel()
        overlayHelper.cleanupOverlayBindings(this)
        super.onDestroy()
    }

    val isOverlayVisible: Boolean
        get() = overlayView?.isAttachedToWindow == true

    fun setIsLocked(value: Boolean) {
        transition { if (value) it.locked() else JoystickInputState.Idle }
        if (!value) resetEngineAfterStick()
        Log.d(TAG, "Joystick locked: $value")
    }

    @Synchronized
    private fun transition(next: (JoystickInputState) -> JoystickInputState?) {
        val current = state.value
        val new =
            next(current) ?: run {
                Log.w(TAG, "ignored transition from $current")
                return
            }
        state.value = new
        _isLocked.value = new.isLocked
        if (current.job !== new.job) current.job?.cancel()
        if (current.isLocked != new.isLocked) (overlayView as? JoystickView)?.isLocked = new.isLocked
    }

    private fun resetEngineAfterStick() {
        // A new automatic movement may have started before the finger is released or unlocked.
        // Its mode and motion vector belong to that engine, not to the old joystick gesture.
        val movement = locationRepository.currentMovement()
        if (!shouldPreserveEngineMode(movement.mode)) {
            locationRepository.setMockMode(MockMode.TELEPORT)
        }
        val engineOwnsMovement = shouldIgnoreJoystickInput(movement)
        if (!engineOwnsMovement) {
            mockLocationService?.clearMotionVector()
        }
    }

    fun showJoystick() {
        showOverlay()
    }

    fun hideJoystick() {
        hideOverlay()
    }

    fun toggleOverlay() {
        if (isOverlayVisible) {
            hideJoystick()
        } else {
            showJoystick()
        }
    }

    override fun createOverlayView(): View {
        val view = JoystickView(this)
        // addView can return before attachment. Publish the actual window lifecycle,
        // including show/hide commands sent directly through the base overlay service.
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    _isVisible.value = true
                }

                override fun onViewDetachedFromWindow(v: View) {
                    _isVisible.value = false
                }
            },
        )

        view.isLocked = state.value.isLocked
        view.inputIgnored = shouldIgnoreJoystickInput(locationRepository.currentMovement())

        view.onInputChanged = { input ->
            // Positive force comes from live touches; centered input stops steps without
            // changing the lock state or taking over an automatic movement.
            // Synchronous and idempotent: a later map action cannot be cancelled by an old touch.
            if (input.force > 0f) mapController.pauseAutomatedMovement()
            transition { it.withInput(input, ::startMovement) }
        }

        view.shouldResetOnRelease = { !state.value.isLocked }

        view.onReleased = {
            if (state.value.isLocked) {
                // Locked mode: job keeps running with lastInput direction (no zero-force emitted
                // by JoystickView when shouldResetOnRelease returns false).
                Log.d(TAG, "Joystick released (locked) — continuing movement")
            } else {
                transition { it.released() }
                resetEngineAfterStick()
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
        val (screenW, screenH) = screenSize()

        return WindowManager
            .LayoutParams(
                sizePx,
                sizePx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (screenW - sizePx).coerceAtLeast(0)
                y = ((screenH - sizePx) / 2).coerceAtLeast(0)
            }
    }

    private fun startMovement(): Job =
        serviceScope.launch {
            while (isActive) {
                delay(AppConstants.JoystickConstants.STEP_MS)
                val input = state.value.input ?: continue
                if (input.force > 0f) {
                    applyJoystickInput(input)
                }
            }
        }

    private suspend fun applyJoystickInput(input: JoystickInput) {
        val currentPos = locationRepository.currentPosition.value ?: return
        val speedMs = _cachedProfile.value?.speedMetersPerSecond ?: return
        val movement = locationRepository.currentMovement()
        val mode = movement.mode
        // Don't let a stray drag tick steal mode from an engine (route replay, roaming, walk-to,
        // follower sync) that's independently advancing position this same tick.
        if (shouldIgnoreJoystickInput(movement)) return
        if (!shouldPreserveEngineMode(mode)) {
            locationRepository.setMockMode(MockMode.JOYSTICK)
        }

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
