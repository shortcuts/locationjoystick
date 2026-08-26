package com.locationjoystick.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.UiConstants

@Composable
fun LjMapIconButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    // 48dp hit box meets the Android minimum touch target while keeping the smaller visual size.
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = containerColor,
            interactionSource = interactionSource,
            modifier = Modifier.size(UiConstants.FAB_CONTAINER_SIZE).scale(scale),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Crossfade(targetState = icon, animationSpec = tween(150), label = "fabIcon") { animatedIcon ->
                    Icon(
                        imageVector = animatedIcon,
                        contentDescription = contentDescription,
                        tint = contentColor,
                        modifier = Modifier.size(UiConstants.FAB_ICON_SIZE),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun LjMapIconButtonPreview() {
    LjMapIconButton(
        icon = LjIcons.MyLocation,
        contentDescription = "My location",
        containerColor = Color(0xFF1976D2),
        contentColor = Color.White,
        onClick = {},
    )
}
