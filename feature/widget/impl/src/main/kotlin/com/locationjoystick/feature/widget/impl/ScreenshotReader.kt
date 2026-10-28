package com.locationjoystick.feature.widget.impl

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.GrayFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Rec. 601 luma of the first [count] [pixels] into [out], the same weights as the bench's gray conversion. */
internal fun lumaInto(
    pixels: IntArray,
    out: ByteArray,
    count: Int = pixels.size,
) {
    for (i in 0 until count) {
        val p = pixels[i]
        out[i] = ((0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)).toInt()).toByte()
    }
}

internal class BitmapGrayFrame(
    private val bitmap: Bitmap,
) : GrayFrame {
    override val width = bitmap.width
    override val height = bitmap.height
    private var argb = IntArray(0)

    override fun read(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        out: ByteArray,
    ) {
        if (argb.size < w * h) argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, x, y, w, h)
        lumaInto(argb, out, w * h)
    }
}

/** Owns the paced screenshot, its software copy and the recycling, so callers never hold a bitmap. */
internal class ScreenshotReader(
    private val service: AccessibilityService,
) {
    private val pacer = ScreenshotPacer(AppConstants.ScreenshotConstants.MIN_GAP_MS, SystemClock::elapsedRealtime)

    /** Runs [block] on a software copy off the main thread, which draws the overlays. Null when no screenshot arrives. */
    suspend fun <T> read(block: (Bitmap) -> T): T? {
        val bitmap = capture() ?: return null
        return withContext(Dispatchers.Default) {
            // A hardware bitmap has no pixel access, so one software copy stays.
            val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
            try {
                block(soft)
            } finally {
                if (soft !== bitmap) soft.recycle()
                bitmap.recycle()
            }
        }
    }

    private suspend fun capture(): Bitmap? {
        // Unreachable in practice — onServiceConnected() never binds below API 30 — but
        // takeScreenshot() itself requires API 30, so lint needs an inline SDK_INT check here
        // too (it doesn't trace version guards through a delegated boolean function).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return pacer.paced { shoot() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun shoot(): Bitmap? =
        suspendCancellableCoroutine { cont ->
            try {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    service.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
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

    private companion object {
        const val TAG = "ScreenshotReader"
    }
}
