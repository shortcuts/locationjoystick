package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjSuccess
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.designsystem.UiConstants

/**
 * Compact `current/total` chip shown at the bottom of the map FAB column and widget icon list
 * while a route replay is active. Visual size matches [UiConstants.FAB_CONTAINER_SIZE]; callers
 * add the same outer inset as the neighbouring icon buttons so left edges line up.
 */
@Composable
fun RouteProgressBadge(
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .height(UiConstants.FAB_CONTAINER_SIZE)
                .defaultMinSize(minWidth = UiConstants.FAB_CONTAINER_SIZE)
                .background(LjBg, RoundedCornerShape(percent = 50))
                .padding(horizontal = 10.dp)
                .semantics { this.contentDescription = contentDescription },
    ) {
        Text(
            text = label,
            color = LjSuccess,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/**
 * Same 48.dp minimum slot as [LjMapIconButton], with the chip centered inside it so the
 * chip's visual left edge lines up with the map FAB circles in an end-aligned column.
 * Min-size rather than a fixed 48.dp box so labels wider than the circle (e.g. `16/41`)
 * can grow instead of clipping.
 */
@Composable
fun RouteProgressBadgeInMapFabSlot(
    label: String,
    contentDescription: String,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
    ) {
        RouteProgressBadge(label = label, contentDescription = contentDescription)
    }
}

@Preview
@Composable
private fun RouteProgressBadgePreview() {
    RouteProgressBadge(label = "1/60", contentDescription = stringResource(R.string.route_progress_badge_stop_1_of_60))
}

@Preview
@Composable
private fun RouteProgressBadgeInMapFabSlotPreview() {
    RouteProgressBadgeInMapFabSlot(label = "16/41", contentDescription = stringResource(R.string.route_progress_badge_stop_16_of_41))
}
