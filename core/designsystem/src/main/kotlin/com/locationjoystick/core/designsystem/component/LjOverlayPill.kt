package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// The translucent surface lets the map show through; the contrast test uses this value.
internal const val LJ_OVERLAY_PILL_ALPHA = 0.92f

@Composable
fun LjOverlayPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = LJ_OVERLAY_PILL_ALPHA),
                    MaterialTheme.shapes.small,
                ).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
