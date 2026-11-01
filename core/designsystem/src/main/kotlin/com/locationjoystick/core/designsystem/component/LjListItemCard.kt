package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjSpacing

/**
 * Shared row layout for a list-of-cards screen (Routes, Favorites): an optional
 * leading slot, a weighted content column plus a trailing action area — built on [LjCard]
 * (flat borderless card) instead of each screen hand-rolling its own
 * `background(surfaceVariant, shapes.small)` row.
 */
@Composable
fun LjListItemCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = if (onClick != null) rememberPressScale(interactionSource) else 1f
    LjCard(
        modifier = modifier.fillMaxWidth().scale(scale),
        onClick = onClick,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = LjSpacing.md, top = LjSpacing.md, end = LjSpacing.xs, bottom = LjSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(LjSpacing.md))
            }
            Column(modifier = Modifier.weight(1f)) {
                content()
            }
            trailing()
        }
    }
}

@Composable
fun LjListItemLeadingIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}
