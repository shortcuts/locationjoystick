package com.locationjoystick.feature.widget.impl

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class TapToWalkOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val onWalkTo: (LatLng) -> Unit,
    private val getPosition: () -> LatLng?,
    private val getScaleMpx: () -> Double,
    private val onDismissed: () -> Unit,
    private val getHeadingAsync: (suspend () -> Float?)? = null,
) {
    private var overlayView: ComposeView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var preCapturedHeading: Float? = null

    fun isShowing(): Boolean = overlayView?.isAttachedToWindow == true

    fun show() {
        if (isShowing()) return
        if (getHeadingAsync != null) {
            scope.launch {
                preCapturedHeading = withTimeoutOrNull(1500L) { getHeadingAsync.invoke() }
            }
        }
        val view =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
                setContent {
                    LjTheme {
                        TapToWalkOverlayContent(
                            onTap = { x, y, screenW, screenH ->
                                val pos = getPosition()
                                if (pos != null) {
                                    val northAngle = preCapturedHeading?.toDouble() ?: 0.0
                                    val newPos = computeWalkTarget(pos, x, y, screenW, screenH, getScaleMpx(), northAngle)
                                    onWalkTo(newPos)
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
        scope.coroutineContext.cancelChildren()
        preCapturedHeading = null
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
         * Converts a screen tap position to a GPS coordinate.
         *
         * [northAngleRad] is the clockwise angle from screen-up to geographic north
         * (atan2 of red-pixel centroid offset). Default 0 = north-up map.
         * Zooming out reduces positioning error per pixel.
         */
        fun computeWalkTarget(
            currentPos: LatLng,
            tapX: Float,
            tapY: Float,
            screenW: Int,
            screenH: Int,
            metersPerPixel: Double,
            northAngleRad: Double = 0.0,
        ): LatLng {
            val rawDx = (tapX - screenW / 2f) * metersPerPixel
            val rawDy = -(tapY - screenH / 2f) * metersPerPixel
            val dx = rawDx * cos(northAngleRad) - rawDy * sin(northAngleRad)
            val dy = rawDx * sin(northAngleRad) + rawDy * cos(northAngleRad)
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
    onTap: (x: Float, y: Float, screenW: Int, screenH: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Background — captures all taps and converts to walk target
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.05f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { offset ->
                            onTap(offset.x, offset.y, size.width, size.height)
                        })
                    },
        )
        // Hint label
        Text(
            text = "Tap anywhere to walk there",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
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
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { _ -> onDismiss() })
                    },
        ) {
            Icon(
                imageVector = LjIcons.Close,
                contentDescription = "Cancel tap-to-walk",
                tint = Color.White,
            )
        }
    }
}
