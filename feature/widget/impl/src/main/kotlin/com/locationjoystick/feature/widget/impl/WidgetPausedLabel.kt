package com.locationjoystick.feature.widget.impl

import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.locationjoystick.core.common.constants.AppConstants.AnimationConstants
import com.locationjoystick.core.designsystem.UiConstants
import com.locationjoystick.core.designsystem.component.LjLiquidLabel
import com.locationjoystick.core.designsystem.component.rememberLiquidLabelProgress

/** Non-touchable child window: the liquid can overlap its source without blocking that button. */
@Composable
internal fun WidgetPausedLabel(
    visible: Boolean,
    controlsExpanded: Boolean,
) {
    val targetVisible = visible && !controlsExpanded
    val progress = rememberLiquidLabelProgress(targetVisible, animate = !controlsExpanded)
    val showWindow by remember(targetVisible) { derivedStateOf { targetVisible || progress.value > 0f } }
    // Controls occupy this same space; remove the status immediately when they open.
    if (controlsExpanded || !showWindow) return

    val parentView = LocalView.current
    val sourceRadiusPx = with(LocalDensity.current) { (UiConstants.FAB_CONTAINER_SIZE / 2).roundToPx() }
    var expandLeft by remember { mutableStateOf(false) }
    val positionProvider =
        remember(parentView, sourceRadiusPx) {
            LiquidLabelPositionProvider(parentView, sourceRadiusPx) { expandLeft = it }
        }
    // Even at the middle of a narrow screen, the label fits on the roomier side.
    val maxLabelWidth =
        LocalConfiguration.current.screenWidthDp.dp / 2 - UiConstants.FAB_CONTAINER_SIZE / 2 -
            AnimationConstants.LIQUID_LABEL_GAP_DP.dp
    Popup(
        popupPositionProvider = positionProvider,
        properties =
            PopupProperties(
                flags =
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                inheritSecurePolicy = true,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                excludeFromSystemGesture = false,
                windowToken = parentView.windowToken ?: parentView.applicationWindowToken,
            ),
    ) {
        LjLiquidLabel(
            text = stringResource(R.string.widget_activity_paused),
            progress = { progress.value },
            expandLeft = expandLeft,
            sourceDiameter = UiConstants.FAB_CONTAINER_SIZE,
            maxLabelWidth = maxLabelWidth,
            containerColor = Color.Black,
            contentColor = Color.White.copy(alpha = 0.82f),
        )
    }
}

@Preview
@Composable
private fun WidgetPausedLabelPreview() {
    Box(Modifier.size(50.dp, 52.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(UiConstants.FAB_CONTAINER_SIZE).background(Color.Black, CircleShape))
        WidgetPausedLabel(visible = true, controlsExpanded = false)
    }
}

private class LiquidLabelPositionProvider(
    private val parentView: View,
    private val sourceRadius: Int,
    private val onSideChanged: (Boolean) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val location = IntArray(2)
        parentView.getLocationOnScreen(location)
        val placement =
            liquidLabelPopupPlacement(
                parentXOnScreen = location[0],
                anchor = anchorBounds,
                popupSize = popupContentSize,
                sourceRadius = sourceRadius,
                screenWidth = parentView.resources.displayMetrics.widthPixels,
            )
        onSideChanged(placement.expandLeft)
        return placement.offset
    }
}

internal data class LiquidLabelPlacement(
    val offset: IntOffset,
    val expandLeft: Boolean,
)

/** Uses the final window width throughout the animation, so the label never flips mid-expansion. */
internal fun liquidLabelPopupPlacement(
    parentXOnScreen: Int,
    anchor: IntRect,
    popupSize: IntSize,
    sourceRadius: Int,
    screenWidth: Int,
): LiquidLabelPlacement {
    val rightX = anchor.center.x - sourceRadius
    val expandLeft = parentXOnScreen + rightX + popupSize.width > screenWidth
    val x = if (expandLeft) anchor.center.x + sourceRadius - popupSize.width else rightX
    return LiquidLabelPlacement(
        offset = IntOffset(x, anchor.center.y - popupSize.height / 2),
        expandLeft = expandLeft,
    )
}
