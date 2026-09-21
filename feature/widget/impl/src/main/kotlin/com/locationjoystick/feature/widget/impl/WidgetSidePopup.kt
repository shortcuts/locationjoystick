package com.locationjoystick.feature.widget.impl

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Places [content] in a WRAP_CONTENT child window beside the caller.
 *
 * Expanding route / group / altitude controls inside the widget [Column] would otherwise
 * widen the TYPE_APPLICATION_OVERLAY hit rectangle, so empty map above and below the
 * extra buttons ate taps meant for the app underneath.
 *
 * Default Compose popup type is TYPE_APPLICATION_SUB_PANEL, attached to the overlay's
 * [View.getWindowToken], so the bar is a child of the widget window: it stays flush with
 * the icon and moves when the widget is dragged. [clippingEnabled] is false so it can draw
 * outside the narrow FAB column. Coordinates returned by [widgetSidePopupOffset] are
 * parent-window-relative (what WindowManager expects for a non-nested sub-panel).
 */
@Composable
internal fun WidgetSidePopup(
    visible: Boolean,
    focusable: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    val parentView = LocalView.current
    val positionProvider = remember(parentView) { WidgetSidePopupPositionProvider(parentView) }
    Popup(
        popupPositionProvider = positionProvider,
        properties =
            PopupProperties(
                focusable = focusable,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                clippingEnabled = false,
                windowToken = parentView.windowToken ?: parentView.applicationWindowToken,
            ),
        onDismissRequest = {},
    ) {
        content()
    }
}

internal class WidgetSidePopupPositionProvider(
    private val parentView: View,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val loc = IntArray(2)
        parentView.getLocationOnScreen(loc)
        val metrics = parentView.resources.displayMetrics
        val (x, y) =
            widgetSidePopupOffset(
                parentXOnScreen = loc[0],
                parentYOnScreen = loc[1],
                anchorLeft = anchorBounds.left,
                anchorTop = anchorBounds.top,
                anchorRight = anchorBounds.right,
                popupWidth = popupContentSize.width,
                popupHeight = popupContentSize.height,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
            )
        return IntOffset(x, y)
    }
}

/**
 * Parent-window-relative offset for a SUB_PANEL beside [anchorLeft]/[anchorRight].
 *
 * Prefers the right of the icon; flips left when the bar would go off the right of the
 * screen. [windowSize] from Compose is the overlay (too small to use for this).
 */
internal fun widgetSidePopupOffset(
    parentXOnScreen: Int,
    parentYOnScreen: Int,
    anchorLeft: Int,
    anchorTop: Int,
    anchorRight: Int,
    popupWidth: Int,
    popupHeight: Int,
    screenWidth: Int,
    screenHeight: Int,
): Pair<Int, Int> {
    val fitsRight = parentXOnScreen + anchorRight + popupWidth <= screenWidth
    val x = if (fitsRight) anchorRight else anchorLeft - popupWidth
    val overflowY = parentYOnScreen + anchorTop + popupHeight - screenHeight
    val y = if (overflowY > 0) anchorTop - overflowY else anchorTop
    return x to y
}
