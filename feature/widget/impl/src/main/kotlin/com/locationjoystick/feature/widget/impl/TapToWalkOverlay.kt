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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.copyCoordinatesToClipboard
import com.locationjoystick.core.common.util.shareLocationLink
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjOnScrim
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.component.LjShareLocationAction
import com.locationjoystick.core.designsystem.component.LjTextButton
import com.locationjoystick.core.designsystem.component.TeleportCooldownBadge
import com.locationjoystick.core.model.CalibrationWalkMath
import com.locationjoystick.core.model.GroundFlowFit
import com.locationjoystick.core.model.GroundMap
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.StoredGroundCalibration
import com.locationjoystick.core.model.TapToWalkMove
import com.locationjoystick.core.model.distanceTo
import com.locationjoystick.feature.widget.impl.R
import kotlinx.coroutines.flow.Flow
import kotlin.math.atan2
import kotlin.math.hypot

internal class TapToWalkOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    /** A tap in full-display pixels with the position read at tap time; the caller resolves it against the ground map. */
    private val onGroundTap: (GroundTap) -> TapDecision,
    private val onMove: (GroundTap, TapToWalkMove) -> Unit,
    private val cooldownForPosition: (LatLng) -> Flow<CooldownState>,
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
                    LjTheme(guidedPath = GuidedPath.DEFAULT) {
                        var chooser by remember { mutableStateOf<PendingChoice?>(null) }
                        TapToWalkOverlayContent(
                            hint = hint,
                            chooser = chooser,
                            cooldownForPosition = cooldownForPosition,
                            onTap = { x, y ->
                                val pos = getPosition()
                                if (pos != null && isSupported()) {
                                    // The map is fitted on full-display frames; system bars may inset the overlay window.
                                    val origin = IntArray(2).also { overlayView?.getLocationOnScreen(it) }
                                    val tap = GroundTap(pos, (origin[0] + x).toDouble(), (origin[1] + y).toDouble())
                                    when (val decision = onGroundTap(tap)) {
                                        TapDecision.Done -> dismiss()
                                        is TapDecision.Choose -> chooser = PendingChoice(tap, decision)
                                    }
                                } else {
                                    if (pos != null) {
                                        Toast.makeText(context, R.string.tap_to_walk_overlay_unavailable_hint, Toast.LENGTH_LONG).show()
                                    }
                                    dismiss()
                                }
                            },
                            onMove = onMove,
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
    chooser: PendingChoice?,
    cooldownForPosition: (LatLng) -> Flow<CooldownState>,
    onTap: (x: Float, y: Float) -> Unit,
    onMove: (GroundTap, TapToWalkMove) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Background — captures all taps and converts to walk target
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.05f))
                    .pointerInput(chooser == null) {
                        // While the chooser is open a stray tap must not start a move.
                        if (chooser == null) {
                            detectTapGestures(onTap = { offset ->
                                onTap(offset.x, offset.y)
                            })
                        }
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
        if (chooser != null) {
            TapMoveChooser(chooser, cooldownForPosition, onMove, onDismiss)
        } else {
            CancelButton(onDismiss)
        }
    }
}

@Composable
internal fun BoxScope.TapMoveChooser(
    chooser: PendingChoice,
    cooldownForPosition: (LatLng) -> Flow<CooldownState>,
    onMove: (GroundTap, TapToWalkMove) -> Unit,
    onDismiss: () -> Unit,
) {
    val target = chooser.decision.target
    val context = LocalContext.current
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable {}
                .padding(LjSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.map_floating_move_to_this_location),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            LjShareLocationAction(
                onCopyCoordinates = { copyCoordinatesToClipboard(context, target) },
                onShareLink = {
                    shareLocationLink(context, target)
                    onDismiss()
                },
            )
        }
        Spacer(Modifier.height(LjSpacing.sm))
        TeleportCooldownBadge(target, cooldownForPosition.asAdvisory())
        Spacer(Modifier.height(LjSpacing.md))
        MoveButtons(chooser.decision.moves) {
            onMove(chooser.tap, it)
            onDismiss()
        }
        Spacer(Modifier.height(LjSpacing.xs))
        LjTextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.map_floating_do_nothing))
        }
    }
}

@Composable
private fun BoxScope.CancelButton(onDismiss: () -> Unit) {
    // Drawn on top; consumes taps in its bounds before the background does.
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

/** A tap waiting for the user's choice of move; the overlay stays up meanwhile. */
internal data class PendingChoice(
    val tap: GroundTap,
    val decision: TapDecision.Choose,
)

/** A Tap to Walk tap: the position at tap time and the full-display pixel, kept so the tap can be re-aimed under a newer map. */
internal data class GroundTap(
    val from: LatLng,
    val x: Double,
    val y: Double,
)
