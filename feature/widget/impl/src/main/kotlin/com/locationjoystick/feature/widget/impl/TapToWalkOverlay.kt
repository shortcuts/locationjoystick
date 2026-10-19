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
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjOnScrim
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.model.CalibrationWalkMath
import com.locationjoystick.core.model.FitHealth
import com.locationjoystick.core.model.GroundMap
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.StoredGroundCalibration
import com.locationjoystick.feature.widget.impl.R
import kotlin.math.atan2
import kotlin.math.hypot

internal class TapToWalkOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val onWalkTo: (LatLng) -> Unit,
    private val getPosition: () -> LatLng?,
    private val onDismissed: () -> Unit,
    private val getGroundCalibration: () -> StoredGroundCalibration? = { null },
    private val getFitHealth: () -> FitHealth = { FitHealth.None },
    private val canLearn: () -> Boolean = { false },
    /** Called instead of [onWalkTo] when the map is not valid and can be learned: [pos] at tap time, then the full-display tap pixel. */
    private val onLearnThenWalk: (pos: LatLng, x: Double, y: Double) -> Unit = { _, _, _ -> },
) {
    private var overlayView: ComposeView? = null

    fun isShowing(): Boolean = overlayView?.isAttachedToWindow == true

    fun show() {
        if (isShowing()) return
        val hint = hintFor(hasUsableGroundMap())
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
                                if (pos != null) {
                                    if (needsLearning()) {
                                        val origin = IntArray(2).also { overlayView?.getLocationOnScreen(it) }
                                        onLearnThenWalk(pos, (origin[0] + x).toDouble(), (origin[1] + y).toDouble())
                                    } else {
                                        val target = resolveTarget(pos, x, y)
                                        if (target != null) {
                                            onWalkTo(target)
                                        } else {
                                            Toast.makeText(context, R.string.tap_to_walk_learning_failed, Toast.LENGTH_LONG).show()
                                        }
                                    }
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
                if (view.isAttachedToWindow) windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dismiss tap-to-walk overlay", e)
            }
            view.disposeComposition()
        }
        overlayView = null
        showing = false
        if (had) onDismissed()
    }

    /** The map is not valid for this display and a learning walk can run: the tap waits for it (see FloatingWidgetService). */
    private fun needsLearning(): Boolean = (getFitHealth() != FitHealth.Valid || !hasUsableGroundMap()) && canLearn()

    private fun hintFor(usable: Boolean): Int =
        if (usable || canLearn()) {
            R.string.tap_to_walk_overlay_tap_anywhere_to_walk_there
        } else {
            R.string.tap_to_walk_overlay_cannot_learn_hint
        }

    /** True when a stored fit matches the full display, the condition [resolveTarget] needs for the ground map. */
    private fun hasUsableGroundMap(): Boolean {
        val stored = getGroundCalibration() ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val bounds = windowManager.maximumWindowMetrics.bounds
        return stored.matches(bounds.width(), bounds.height())
    }

    /**
     * Ground-map path when a stored calibration matches the full display, else null: a tap with no
     * map never walks a guessed bearing. The map is fitted on full-display frames, so the tap is
     * shifted by the window's on-screen origin (system bars may inset the overlay).
     */
    private fun resolveTarget(
        pos: LatLng,
        x: Float,
        y: Float,
    ): LatLng? {
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
        return null
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

        /** Read by the reconnect gate: a gesture injected while this window is up lands on it. */
        @Volatile
        var showing = false
            private set

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
