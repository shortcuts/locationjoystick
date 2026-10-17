package com.locationjoystick.feature.widget.impl

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjOnScrim
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.model.CalibrationWalkMath
import com.locationjoystick.core.model.GroundMap
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.StoredGroundCalibration
import com.locationjoystick.feature.widget.impl.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

internal class TapToWalkOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val onWalkTo: (LatLng) -> Unit,
    private val getPosition: () -> LatLng?,
    private val getScaleMpx: () -> Double,
    private val onDismissed: () -> Unit,
    private val getGroundCalibration: () -> StoredGroundCalibration? = { null },
) {
    private var overlayView: ComposeView? = null

    fun isShowing(): Boolean = overlayView?.isAttachedToWindow == true

    fun show() {
        if (isShowing()) return
        val usable = hasUsableGroundMap()
        val view =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
                setContent {
                    LjTheme {
                        TapToWalkOverlayContent(
                            calibrated = usable,
                            onTap = { x, y, screenW, screenH ->
                                val pos = getPosition()
                                if (pos != null) {
                                    onWalkTo(resolveTarget(pos, x, y, screenW, screenH))
                                }
                                dismiss()
                            },
                            onDismiss = { dismiss() },
                        )
                    }
                }
            }
        overlayView = view
        try {
            windowManager.addView(view, overlayLayoutParams())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show tap-to-walk overlay", e)
            overlayView = null
            onDismissed()
        }
    }

    fun dismiss() {
        val had = overlayView != null
        overlayView?.let { view ->
            try {
                if (view.isAttachedToWindow) windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dismiss tap-to-walk overlay", e)
            }
            view.disposeComposition()
        }
        overlayView = null
        if (had) onDismissed()
    }

    /** True when a stored calibration matches the full display, the condition [resolveTarget] needs for the ground map. */
    private fun hasUsableGroundMap(): Boolean {
        val stored = getGroundCalibration() ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val bounds = windowManager.maximumWindowMetrics.bounds
        return stored.matches(bounds.width(), bounds.height())
    }

    /**
     * Ground-map path when a stored calibration matches the full display; otherwise the north-up scale
     * formula. The map is fitted on full-display frames, so the tap is shifted by the window's
     * on-screen origin (system bars may inset the overlay).
     */
    private fun resolveTarget(
        pos: LatLng,
        x: Float,
        y: Float,
        screenW: Int,
        screenH: Int,
    ): LatLng {
        val stored = getGroundCalibration()
        if (stored != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            if (stored.matches(bounds.width(), bounds.height())) {
                val origin = IntArray(2).also { overlayView?.getLocationOnScreen(it) }
                val map = GroundMap(stored.calibration, bounds.width().toDouble(), bounds.height().toDouble())
                val target = computeGroundWalkTarget(pos, map, (origin[0] + x).toDouble(), (origin[1] + y).toDouble())
                if (target != null) {
                    Log.d(TAG, "tap resolved via ground map")
                    return target
                }
            }
        }
        Log.d(TAG, "tap resolved via north-up fallback")
        return computeWalkTarget(pos, x, y, screenW, screenH, getScaleMpx())
    }

    private fun overlayLayoutParams() =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // No FLAG_NOT_TOUCH_MODAL — intercepts all taps
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )

    companion object {
        private const val TAG = "TapToWalkOverlay"

        /**
         * Ground-map conversion of a full-display tap pixel to a GPS coordinate, or null past the
         * horizon. The offset is applied with the calibration walk's flat-earth frame (111320 m/deg).
         */
        fun computeGroundWalkTarget(
            currentPos: LatLng,
            map: GroundMap,
            tapX: Double,
            tapY: Double,
        ): LatLng? {
            val o = map.pixelToGround(tapX, tapY) ?: return null
            val bearing = Math.toDegrees(atan2(o.eastMeters, o.northMeters))
            return CalibrationWalkMath.commandedTarget(currentPos, bearing, hypot(o.eastMeters, o.northMeters))
        }

        /**
         * Fallback conversion of a screen tap position to a GPS coordinate, assuming a north-up map
         * at [metersPerPixel]. Zooming out reduces positioning error per pixel.
         */
        fun computeWalkTarget(
            currentPos: LatLng,
            tapX: Float,
            tapY: Float,
            screenW: Int,
            screenH: Int,
            metersPerPixel: Double,
        ): LatLng {
            val dx = (tapX - screenW / 2f) * metersPerPixel
            val dy = -(tapY - screenH / 2f) * metersPerPixel
            val lat = currentPos.latitude + (dy / AppConstants.LocationConstants.EARTH_RADIUS_METERS) * (180.0 / PI)
            val lon =
                currentPos.longitude +
                    (dx / AppConstants.LocationConstants.EARTH_RADIUS_METERS) * (180.0 / PI) /
                    cos(currentPos.latitude * PI / 180.0)
            return LatLng(lat.coerceIn(-90.0, 90.0), lon)
        }
    }
}

@Composable
private fun TapToWalkOverlayContent(
    calibrated: Boolean,
    onTap: (x: Float, y: Float, screenW: Int, screenH: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Background — captures all taps and converts to walk target
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.05f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { offset ->
                            onTap(offset.x, offset.y, size.width, size.height)
                        })
                    },
        )
        // Hint label
        Text(
            text =
                stringResource(
                    if (calibrated) {
                        R.string.tap_to_walk_overlay_tap_anywhere_to_walk_there
                    } else {
                        R.string.tap_to_walk_overlay_not_calibrated_hint
                    },
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = LjOnScrim,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // Cancel button — drawn on top; consumes taps in its bounds before background does
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button, onClick = onDismiss),
        ) {
            Icon(
                imageVector = LjIcons.Close,
                contentDescription = stringResource(R.string.tap_to_walk_overlay_cancel_tap_to_walk_cd),
                tint = LjOnScrim,
            )
        }
    }
}
