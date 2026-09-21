package com.locationjoystick.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.LjWarning
import com.locationjoystick.core.designsystem.LjWarningContainer

@Composable
fun LjGuidedStepCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    actionLabel: String,
    modifier: Modifier = Modifier,
    extraActionLabel: String? = null,
    onAction: () -> Unit,
    onExtraAction: (() -> Unit)? = null,
) {
    val statusColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.secondary else LjWarning,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statusColor",
    )
    val statusContainerColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.secondaryContainer else LjWarningContainer,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statusContainerColor",
    )

    LjCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(statusContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState = isGranted,
                        animationSpec = tween(150),
                        label = "stepCardIcon",
                    ) { granted ->
                        Icon(
                            imageVector = if (granted) LjIcons.CheckCircle else icon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAction,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    if (extraActionLabel != null && onExtraAction != null) {
                        TextButton(onClick = onExtraAction) {
                            Text(
                                text = extraActionLabel,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LjGuidedStepCardPreview() {
    LjTheme {
        LjGuidedStepCard(
            title = "Example step",
            description = "Description of what this step does.",
            isGranted = false,
            icon = LjIcons.LocationOn,
            actionLabel = "Grant",
            onAction = {},
        )
    }
}
