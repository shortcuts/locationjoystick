package com.locationjoystick.feature.widget.impl

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.location.CompassAccessibilityServiceBridge
import com.locationjoystick.core.location.CompassHeadingSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@AndroidEntryPoint
class CompassAccessibilityService :
    AccessibilityService(),
    CompassAccessibilityServiceBridge {
    companion object {
        private const val TAG = "CompassAccessibilitySvc"

        /**
         * `takeScreenshot(int, Executor, TakeScreenshotCallback)` requires API 30 — no fallback exists.
         */
        fun isSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.R

        /**
         * Auto-locates the compass needle icon in [bitmap] and returns the clockwise angle from
         * screen-up to the direction it points (radians), or null if no matching icon is found.
         *
         * No user calibration: searches a fixed top-right region
         * (`AppConstants.CompassTrackingConstants.SEARCH_X_MIN_PCT`/`SEARCH_Y_MAX_PCT`) — where
         * every tested AR/GPS-spoofing game places its compass — for a small, compact red blob
         * (the needle's colored half) sized like an icon rather than a stray red pixel or a large
         * red UI element (a gym marker, a raid egg) elsewhere on screen. Connected-component
         * labelling groups red pixels into blobs; only blobs whose bounding box falls within
         * `MIN_ICON_FRACTION`–`MAX_ICON_FRACTION` of the screen's short side, and whose pixel
         * count clears `MIN_RED_PIXELS`, are considered — this is what makes detection immune to
         * unrelated red clutter nearby, unlike a plain "reddest pixel in a big circle" scan.
         *
         * The pointing direction is the blob's own principal axis (PCA on the red pixel
         * coordinates), signed by third-moment skew so it points from the wide/pivot end toward
         * the tapered tip — not a "bottom-center of the bounding box" approximation, which only
         * held when the needle pointed straight up and drifted further off the true angle the
         * more the needle rotated (see `docs/features/tap-to-walk.md`). Self-calibrating every
         * call, no stored region needed.
         */
        fun detectNorthAngle(bitmap: Bitmap): Float? {
            val soft =
                if (bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bitmap
                }
            val width = soft.width
            val height = soft.height
            val searchX = (AppConstants.CompassTrackingConstants.SEARCH_X_MIN_PCT * width).toInt()
            val searchW = width - searchX
            val searchH = (AppConstants.CompassTrackingConstants.SEARCH_Y_MAX_PCT * height).toInt()
            if (searchW <= 0 || searchH <= 0) {
                if (soft !== bitmap) soft.recycle()
                return null
            }

            val pixels = IntArray(searchW * searchH)
            soft.getPixels(pixels, 0, searchW, searchX, 0, searchW, searchH)
            if (soft !== bitmap) soft.recycle()

            val isRed = BooleanArray(pixels.size)
            val hsv = FloatArray(3)
            for (i in pixels.indices) {
                Color.colorToHSV(pixels[i], hsv)
                isRed[i] = (hsv[0] < 15f || hsv[0] > 345f) && hsv[1] > 0.5f && hsv[2] > 0.3f
            }

            val shortSide = minOf(width, height)
            val minDim = (AppConstants.CompassTrackingConstants.MIN_ICON_FRACTION * shortSide).toInt().coerceAtLeast(4)
            val maxDim = (AppConstants.CompassTrackingConstants.MAX_ICON_FRACTION * shortSide).toInt()

            val blob = findBestIconBlob(isRed, searchW, searchH, minDim, maxDim) ?: return null

            // Principal axis of the blob's own pixel distribution — rotation-invariant, unlike
            // deriving a pivot from the bounding box.
            val varX = blob.sumXX / blob.count - blob.centroidX * blob.centroidX
            val varY = blob.sumYY / blob.count - blob.centroidY * blob.centroidY
            val covXY = blob.sumXY / blob.count - blob.centroidX * blob.centroidY
            val theta = 0.5 * atan2(2 * covXY, varX - varY)
            var ax = cos(theta)
            var ay = sin(theta)

            // Sign the axis toward the tapered tip: a teardrop/kite needle has more mass at its
            // wide (pivot) end, so the third moment along the tip-ward direction is positive.
            var m3 = 0.0
            for (y in blob.minY..blob.maxY) {
                for (x in blob.minX..blob.maxX) {
                    if (isRed[y * searchW + x]) {
                        val proj = (x - blob.centroidX) * ax + (y - blob.centroidY) * ay
                        m3 += proj * proj * proj
                    }
                }
            }
            if (m3 < 0) {
                ax = -ax
                ay = -ay
            }
            return atan2(ax, -ay).toFloat()
        }

        private class IconBlob(
            val centroidX: Double,
            val centroidY: Double,
            val sumXX: Double,
            val sumYY: Double,
            val sumXY: Double,
            val minX: Int,
            val maxX: Int,
            val minY: Int,
            val maxY: Int,
            val count: Int,
        )

        /** 4-connected component search over [isRed], keeping only the largest icon-sized blob. */
        private fun findBestIconBlob(
            isRed: BooleanArray,
            w: Int,
            h: Int,
            minDim: Int,
            maxDim: Int,
        ): IconBlob? {
            val visited = BooleanArray(isRed.size)
            val queueX = IntArray(isRed.size)
            val queueY = IntArray(isRed.size)
            var best: IconBlob? = null

            for (sy in 0 until h) {
                for (sx in 0 until w) {
                    val startIdx = sy * w + sx
                    if (!isRed[startIdx] || visited[startIdx]) continue

                    var head = 0
                    var tail = 0
                    queueX[tail] = sx
                    queueY[tail] = sy
                    tail++
                    visited[startIdx] = true
                    var minX = sx
                    var maxX = sx
                    var minY = sy
                    var maxY = sy
                    var sumX = 0.0
                    var sumY = 0.0
                    var sumXX = 0.0
                    var sumYY = 0.0
                    var sumXY = 0.0
                    var count = 0

                    while (head < tail) {
                        val cx = queueX[head]
                        val cy = queueY[head]
                        head++
                        count++
                        sumX += cx
                        sumY += cy
                        sumXX += cx.toDouble() * cx
                        sumYY += cy.toDouble() * cy
                        sumXY += cx.toDouble() * cy
                        if (cx < minX) minX = cx
                        if (cx > maxX) maxX = cx
                        if (cy < minY) minY = cy
                        if (cy > maxY) maxY = cy

                        if (cx > 0) enqueueIfRed(cx - 1, cy, w, isRed, visited, queueX, queueY, tail)?.let { tail = it }
                        if (cx < w - 1) enqueueIfRed(cx + 1, cy, w, isRed, visited, queueX, queueY, tail)?.let { tail = it }
                        if (cy > 0) enqueueIfRed(cx, cy - 1, w, isRed, visited, queueX, queueY, tail)?.let { tail = it }
                        if (cy < h - 1) enqueueIfRed(cx, cy + 1, w, isRed, visited, queueX, queueY, tail)?.let { tail = it }
                    }

                    val bboxW = maxX - minX + 1
                    val bboxH = maxY - minY + 1
                    if (bboxW in minDim..maxDim &&
                        bboxH in minDim..maxDim &&
                        count >= AppConstants.CompassTrackingConstants.MIN_RED_PIXELS
                    ) {
                        val current = best
                        if (current == null || count > current.count) {
                            best =
                                IconBlob(
                                    sumX / count,
                                    sumY / count,
                                    sumXX,
                                    sumYY,
                                    sumXY,
                                    minX,
                                    maxX,
                                    minY,
                                    maxY,
                                    count,
                                )
                        }
                    }
                }
            }
            return best
        }

        private fun enqueueIfRed(
            nx: Int,
            ny: Int,
            w: Int,
            isRed: BooleanArray,
            visited: BooleanArray,
            queueX: IntArray,
            queueY: IntArray,
            tail: Int,
        ): Int? {
            val idx = ny * w + nx
            if (!isRed[idx] || visited[idx]) return null
            visited[idx] = true
            queueX[tail] = nx
            queueY[tail] = ny
            return tail + 1
        }
    }

    @Inject lateinit var compassHeadingSource: CompassHeadingSource

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!isSupported()) {
            Log.d(TAG, "Service connected — skipping bind, API ${Build.VERSION.SDK_INT} < R")
            return
        }
        compassHeadingSource.bind(this)
        Log.d(TAG, "Service connected — bound to CompassHeadingSource")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        compassHeadingSource.unbind()
        Log.d(TAG, "Service unbound")
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override suspend fun captureHeading(): Float? {
        val bitmap = takeScreenshotBitmap() ?: return null
        val result = detectNorthAngle(bitmap)
        bitmap.recycle()
        return result
    }

    private suspend fun takeScreenshotBitmap(): Bitmap? {
        // Unreachable in practice — onServiceConnected() never binds below API 30 — but
        // takeScreenshot() itself requires API 30, so lint needs an inline SDK_INT check here
        // too (it doesn't trace version guards through a delegated boolean function).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { cont ->
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
                            cont.resume(bmp)
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
}
