package com.locationjoystick.feature.widget.impl

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.ScreenAccessibilityServiceBridge
import com.locationjoystick.core.location.ScreenCaptureSource
import com.locationjoystick.core.model.GroundFlowFit
import com.locationjoystick.core.model.ReconnectStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

@AndroidEntryPoint
class ScreenAccessibilityService :
    AccessibilityService(),
    ScreenAccessibilityServiceBridge {
    companion object {
        private const val TAG = "ScreenAccessibilitySvc"

        /**
         * `takeScreenshot(int, Executor, TakeScreenshotCallback)` requires API 30 — no fallback exists.
         */
        fun isSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.R
    }

    @Inject lateinit var screenCaptureSource: ScreenCaptureSource

    @Inject lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Debug builds never back off: the reliability matrix needs a steady short cycle.
    private val backoff =
        if (BuildConfig.DEBUG) {
            ReconnectBackoff(
                AppConstants.ReconnectTapConstants.DEBUG_CHECK_INTERVAL_MS,
                AppConstants.ReconnectTapConstants.DEBUG_CHECK_INTERVAL_MS,
            )
        } else {
            ReconnectBackoff(
                AppConstants.ReconnectTapConstants.CHECK_INTERVAL_MS,
                AppConstants.ReconnectTapConstants.MAX_CHECK_INTERVAL_MS,
            )
        }

    // Package of the last window-state-changed event. Only the package name is read, never the view tree.
    @Volatile private var foregroundPackage: String? = null

    private val notifier = ReconnectNotifier(this)
    private val screenshotReader = ScreenshotReader(this)

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!isSupported()) {
            Log.d(TAG, "Service connected — skipping bind, API ${Build.VERSION.SDK_INT} < R")
            return
        }
        screenCaptureSource.bind(this)
        notifier.createChannel()
        Log.d(TAG, "Service connected — bound to ScreenCaptureSource")
        startReconnectLoop()
    }

    /**
     * Periodic reconnect check (docs/features/bluetooth-reconnect-tap.md). Runs only while the
     * setting, the accessibility disclosure and the guided path allow it; `collectLatest` cancels a run in flight the
     * moment any of them turns off.
     */
    private fun startReconnectLoop() {
        serviceScope.launch {
            combine(
                settingsRepository.getReconnectTapEnabled(),
                settingsRepository.getAccessibilityDisclosureAccepted(),
                settingsRepository.getGuidedPath(),
                ::reconnectLoopActive,
            ).distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) {
                        screenCaptureSource.reportReconnect(null)
                        notifier.reset()
                    }
                    while (active) {
                        delay(backoff.delayMs)
                        guardedReconnectCheck(
                            onError = { Log.e(TAG, "Reconnect check failed; next interval retries", it) },
                        ) { runReconnectCheck() }
                    }
                }
        }
    }

    /** Reads only the detector's search window (~16% of the frame) so the heap stays free for the auto-fit analysis. */
    private fun detectControl(bitmap: Bitmap): ReconnectControl? {
        val w = bitmap.width
        val x0 = ReconnectControlDetector.searchX0(w)
        val h = bitmap.height
        val y1 = ReconnectControlDetector.searchY1(h)
        val pixels = IntArray((w - x0) * y1)
        bitmap.getPixels(pixels, 0, w - x0, x0, 0, w - x0, y1)
        return ReconnectControlDetector.detect(pixels, w, h, cropX = x0)
    }

    private suspend fun runReconnectCheck() {
        val result =
            runReconnectTap(
                screenWidth = resources.displayMetrics.widthPixels,
                screenHeight = resources.displayMetrics.heightPixels,
                gate = {
                    reconnectRunGateOpen(
                        getSystemService(PowerManager::class.java).isInteractive,
                        foregroundPackage,
                        TapToWalkOverlay.showing,
                    )
                },
                capture = {
                    val control = screenshotReader.read(::detectControl)
                    Log.d(TAG, "Reconnect read: ${control?.state ?: "NONE"}")
                    control
                },
                tap = { x, y, durationMs ->
                    Log.d(TAG, "Reconnect tap: $x,$y,$durationMs")
                    dispatchTap(x, y, durationMs).also { Log.d(TAG, "Reconnect tap result: $it") }
                },
            )
        Log.d(TAG, "Reconnect check: $result")
        screenCaptureSource.reportReconnect(ReconnectStatus(result, System.currentTimeMillis()))
        backoff.record(result)
        notifier.onResult(result)
    }

    private suspend fun dispatchTap(
        x: Int,
        y: Int,
        durationMs: Long,
    ): Boolean =
        suspendCancellableCoroutine { cont ->
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val gesture =
                GestureDescription
                    .Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                    .build()
            val dispatched =
                dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            cont.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            cont.resume(false)
                        }
                    },
                    null,
                )
            if (!dispatched) cont.resume(false)
        }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        screenCaptureSource.unbind()
        notifier.cancelAll()
        serviceScope.cancel()
        Log.d(TAG, "Service unbound")
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            foregroundPackage =
                foregroundAfterWindowEvent(foregroundPackage, event.packageName?.toString(), event.className?.toString(), packageName)
        }
    }

    override fun onInterrupt() = Unit

    override val gameInFront: Boolean
        get() = reconnectGateOpen(getSystemService(PowerManager::class.java).isInteractive, foregroundPackage)

    override suspend fun captureCrops(): GroundFlowFit.Crops? = screenshotReader.read { GroundFlowFit.crops(BitmapGrayFrame(it)) }
}
