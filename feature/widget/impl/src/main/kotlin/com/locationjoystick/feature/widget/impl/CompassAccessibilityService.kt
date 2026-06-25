package com.locationjoystick.feature.widget.impl

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
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

@AndroidEntryPoint
class CompassAccessibilityService :
    AccessibilityService(),
    CompassAccessibilityServiceBridge {
    companion object {
        private const val TAG = "CompassAccessibilitySvc"

        /**
         * Detects the angle of the red north arrow inside a circular region of [bitmap].
         *
         * [cxPct], [cyPct] are the center as fractions of bitmap width/height.
         * [radiusPct] is the radius as a fraction of min(width, height).
         *
         * Returns clockwise angle from screen-up to the centroid of red pixels (radians),
         * or null if fewer than [AppConstants.CompassTrackingConstants.MIN_RED_PIXELS] red pixels found.
         */
        fun detectNorthAngle(
            bitmap: Bitmap,
            cxPct: Float,
            cyPct: Float,
            radiusPct: Float,
        ): Float? {
            val soft =
                if (bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bitmap
                }
            val cx = (cxPct * soft.width).toInt()
            val cy = (cyPct * soft.height).toInt()
            val radius = (radiusPct * minOf(soft.width, soft.height)).toInt()
            val hsv = FloatArray(3)
            var sumDx = 0.0
            var sumDy = 0.0
            var redCount = 0
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    if (dx * dx + dy * dy > radius * radius) continue
                    val px = cx + dx
                    val py = cy + dy
                    if (px < 0 || px >= soft.width || py < 0 || py >= soft.height) continue
                    Color.colorToHSV(soft.getPixel(px, py), hsv)
                    val hue = hsv[0]
                    val sat = hsv[1]
                    val value = hsv[2]
                    val isRed = (hue < 15f || hue > 345f) && sat > 0.5f && value > 0.3f
                    if (isRed) {
                        sumDx += dx
                        sumDy += dy
                        redCount++
                    }
                }
            }
            if (soft !== bitmap) soft.recycle()
            if (redCount < AppConstants.CompassTrackingConstants.MIN_RED_PIXELS) return null
            val centroidDx = sumDx / redCount
            val centroidDy = sumDy / redCount
            return atan2(centroidDx, -centroidDy).toFloat()
        }
    }

    @Inject lateinit var compassHeadingSource: CompassHeadingSource

    override fun onServiceConnected() {
        super.onServiceConnected()
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

    override suspend fun captureHeading(
        cx: Float,
        cy: Float,
        radius: Float,
    ): Float? {
        val bitmap =
            suspendCancellableCoroutine<Bitmap?> { cont ->
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
        return bitmap?.let { bmp ->
            val result = detectNorthAngle(bmp, cx, cy, radius)
            bmp.recycle()
            result
        }
    }
}
