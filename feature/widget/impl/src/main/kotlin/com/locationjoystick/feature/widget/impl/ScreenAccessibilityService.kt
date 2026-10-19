package com.locationjoystick.feature.widget.impl

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.ScreenAccessibilityServiceBridge
import com.locationjoystick.core.location.ScreenCaptureSource
import com.locationjoystick.core.model.GrayImage
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
import kotlinx.coroutines.withContext
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

    // In memory only: a process restart at worst delays one notification.
    private val failureStreak = ReconnectFailureStreak()
    private val notFoundStreak = ReconnectNotFoundStreak()

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

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!isSupported()) {
            Log.d(TAG, "Service connected — skipping bind, API ${Build.VERSION.SDK_INT} < R")
            return
        }
        screenCaptureSource.bind(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                AppConstants.NotificationConstants.CHANNEL_ID_RECONNECT_FAILED,
                getString(R.string.reconnect_failed_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        Log.d(TAG, "Service connected — bound to ScreenCaptureSource")
        startReconnectLoop()
    }

    /**
     * Periodic reconnect check (docs/features/bluetooth-reconnect-tap.md). Runs only while the
     * setting, both disclosures and the guided path allow it; `collectLatest` cancels a run in flight the moment any
     * of them turns off.
     */
    private fun startReconnectLoop() {
        serviceScope.launch {
            combine(
                settingsRepository.getReconnectTapEnabled(),
                settingsRepository.getReconnectTapDisclosureAccepted(),
                settingsRepository.getAccessibilityDisclosureAccepted(),
                settingsRepository.getGuidedPath(),
                ::reconnectLoopActive,
            ).distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) screenCaptureSource.reportReconnect(null)
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
        val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val w = soft.width
        val x0 = ReconnectControlDetector.searchX0(w)
        val h = soft.height
        val y1 = ReconnectControlDetector.searchY1(h)
        val pixels = IntArray((w - x0) * y1)
        soft.getPixels(pixels, 0, w - x0, x0, 0, w - x0, y1)
        if (soft !== bitmap) soft.recycle()
        bitmap.recycle()
        return ReconnectControlDetector.detect(pixels, w, h, cropX = x0)
    }

    private suspend fun runReconnectCheck() {
        val result =
            runReconnectTap(
                screenWidth = resources.displayMetrics.widthPixels,
                gate = {
                    reconnectRunGateOpen(
                        getSystemService(PowerManager::class.java).isInteractive,
                        foregroundPackage,
                        TapToWalkOverlay.showing,
                    )
                },
                capture = {
                    val bitmap = takeScreenshotBitmap()
                    // The main thread draws the overlays: copy, read and scan elsewhere.
                    val control = bitmap?.let { withContext(Dispatchers.Default) { detectControl(it) } }
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
        if (failureStreak.record(result)) {
            postReconnectNotification(
                R.string.reconnect_failed_title,
                R.string.reconnect_failed_text,
                AppConstants.NotificationConstants.ID_RECONNECT_FAILED,
            )
        }
        if (notFoundStreak.record(result)) {
            postReconnectNotification(
                R.string.reconnect_not_found_title,
                R.string.reconnect_not_found_text,
                AppConstants.NotificationConstants.ID_RECONNECT_NOT_FOUND,
            )
        }
    }

    private fun postReconnectNotification(
        @StringRes title: Int,
        @StringRes text: Int,
        id: Int,
    ) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        val openApp =
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
        val notification =
            NotificationCompat
                .Builder(this, AppConstants.NotificationConstants.CHANNEL_ID_RECONNECT_FAILED)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(getString(title))
                .setContentText(getString(text))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        // areNotificationsEnabled() above covers the POST_NOTIFICATIONS check lint cannot trace.
        @Suppress("MissingPermission")
        manager.notify(id, notification)
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

    override suspend fun captureGrayFrame(): GrayImage? {
        val bitmap = takeScreenshotBitmap() ?: return null
        val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val w = soft.width
        val h = soft.height
        val pixels = IntArray(w * h)
        soft.getPixels(pixels, 0, w, 0, 0, w, h)
        if (soft !== bitmap) soft.recycle()
        bitmap.recycle()
        // Rec. 601 luma, the same weights as the bench's gray conversion.
        val gray =
            ByteArray(w * h) {
                val p = pixels[it]
                ((0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)).toInt()).toByte()
            }
        return GrayImage(w, h, gray)
    }

    private val screenshotPacer =
        ScreenshotPacer(AppConstants.ScreenshotConstants.MIN_GAP_MS, SystemClock::elapsedRealtime)

    private suspend fun takeScreenshotBitmap(): Bitmap? {
        // Unreachable in practice — onServiceConnected() never binds below API 30 — but
        // takeScreenshot() itself requires API 30, so lint needs an inline SDK_INT check here
        // too (it doesn't trace version guards through a delegated boolean function).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return screenshotPacer.paced { shoot() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun shoot(): Bitmap? =
        suspendCancellableCoroutine { cont ->
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            val bmp =
                                screenshot.hardwareBuffer?.let {
                                    Bitmap.wrapHardwareBuffer(it, null)
                                }
                            screenshot.hardwareBuffer?.close()
                            cont.resume(bmp) { bmp?.recycle() }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "takeScreenshot failed with code $errorCode")
                            cont.resume(null)
                        }
                    },
                )
            } catch (e: Exception) {
                Log.e(TAG, "takeScreenshot threw", e)
                cont.resume(null)
            }
        }
}
