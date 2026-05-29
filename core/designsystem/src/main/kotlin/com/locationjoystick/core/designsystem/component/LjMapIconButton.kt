package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
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
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(UiConstants.FAB_CONTAINER_SIZE),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(UiConstants.FAB_ICON_SIZE),
            )
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
