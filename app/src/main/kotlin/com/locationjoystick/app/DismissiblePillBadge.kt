package com.locationjoystick.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons

/**
 * Shared home-screen badge pill (What's New, update available): icon, label and an inline
 * circular dismiss target. [onClick] fires on the pill body, [onDismiss] only on the "X".
 */
@Composable
internal fun DismissiblePillBadge(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    dismissDescription: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "dismissiblePillBadgeScale")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .semantics { this.contentDescription = contentDescription }
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    // ponytail: 32dp trades below Material's 48dp full touch-target guidance for a
                    // visibly smaller pill; still clears WCAG 2.5.8's 24dp AA minimum for a secondary action.
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
        ) {
            Icon(
                imageVector = LjIcons.Close,
                contentDescription = dismissDescription,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
