package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.locationjoystick.core.designsystem.UiConstants

/**
 * Centers [content] in a column clamped to [UiConstants.WIDE_CONTENT_MAX_WIDTH] so screen
 * content doesn't stretch edge-to-edge on wide viewports (tablets, foldables). Shared by every
 * screen that needs this clamp so they can't drift apart on the max-width value the way
 * IdleDestinationCard/SettingsDestinationCard did before [DestinationCard] was extracted.
 */
@Composable
fun WideContentClamp(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = contentModifier.widthIn(max = UiConstants.WIDE_CONTENT_MAX_WIDTH),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}
