package com.locationjoystick.feature.widget.impl

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.StringRes
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
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.model.CalibrationWalkMath
import com.locationjoystick.core.model.GroundFlowFit
import com.locationjoystick.core.model.GroundMap
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.StoredGroundCalibration
import com.locationjoystick.core.model.distanceTo
import com.locationjoystick.feature.widget.impl.R
import kotlin.math.atan2
import kotlin.math.hypot

internal class TapToWalkOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    /** A tap in full-display pixels with the position read at tap time; the caller resolves it against the ground map. */
    private val onGroundTap: (GroundTap) -> Unit,
    private val getPosition: () -> LatLng?,
    private val onDismissed: () -> Unit,
) {
    private var overlayView: ComposeView? = null

    fun isShowing(): Boolean = overlayView != null

    fun show() {
        if (isShowing()) return
        val hint =
            if (isSupported()) R.string.tap_to_walk_overlay_tap_anywhere_to_walk_there else R.string.tap_to_walk_overlay_unavailable_hint
        val view =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
                setContent {
                    LjTheme {
                        TapToWalkOverlayContent(
                            hint = hint,
                            onTap = { x, y ->
                                val pos = getPosition()
                                if (pos != null && isSupported()) {
                                    // The map is fitted on full-display frames; system bars may inset the overlay window.
                                    val origin = IntArray(2).also { overlayView?.getLocationOnScreen(it) }
                                    onGroundTap(GroundTap(pos, (origin[0] + x).toDouble(), (origin[1] + y).toDouble()))
                                } else if (pos != null) {
                                    Toast.makeText(context, R.string.tap_to_walk_overlay_unavailable_hint, Toast.LENGTH_LONG).show()
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
            showing = true
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
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dismiss tap-to-walk overlay", e)
            }
            view.disposeComposition()
        }
        overlayView = null
        showing = false
        if (had) onDismissed()
    }

    // Screenshots need API 30: below it no flow can ever correct the provisional map.
    private fun isSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

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

        /** Read by the reconnect gate: a gesture injected while this window is up lands on it. */
        @Volatile
        var showing = false
            private set

        /** The stored map when it was measured on this display, else the provisional one a fresh install starts with. */
        fun groundMapFor(
            stored: StoredGroundCalibration?,
            width: Int,
            height: Int,
        ): GroundMap {
            val cal = stored?.takeIf { it.matches(width, height) }?.calibration ?: GroundFlowFit.provisional()
            return GroundMap(cal, width.toDouble(), height.toDouble())
        }

        /**
         * Ground-map conversion of a full-display tap pixel to a GPS coordinate, or null past the
         * horizon. The offset is applied with the fit's flat-earth frame (111320 m/deg).
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

        /** The tap's new target under [map], or null when its walk ended or was replaced, or the move is under the threshold. */
        fun reaimedTarget(
            tap: GroundTap,
            aimed: LatLng,
            walkTarget: LatLng?,
            map: GroundMap?,
        ): LatLng? {
            if (walkTarget != aimed || map == null) return null
            val next = computeGroundWalkTarget(tap.from, map, tap.x, tap.y) ?: return null
            return next.takeIf { it.distanceTo(aimed) >= AppConstants.TapToWalkConstants.REAIM_MIN_METERS }
        }
    }
}

@Composable
private fun TapToWalkOverlayContent(
    @StringRes hint: Int,
    onTap: (x: Float, y: Float) -> Unit,
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
                            onTap(offset.x, offset.y)
                        })
                    },
        )
        // Hint label
        Text(
            text = stringResource(hint),
            style = MaterialTheme.typography.bodyMedium,
            color = LjOnScrim,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                    .padding(horizontal = LjSpacing.md, vertical = LjSpacing.sm),
        )
        // Cancel button — drawn on top; consumes taps in its bounds before background does
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(LjSpacing.lg)
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

/** A Tap to Walk tap: the position at tap time and the full-display pixel, kept so the tap can be re-aimed under a newer map. */
internal data class GroundTap(
    val from: LatLng,
    val x: Double,
    val y: Double,
)
