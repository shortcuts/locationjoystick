package com.locationjoystick.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun LjTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) LjDarkColorScheme else LjLightColorScheme,
        typography = LjTypography,
        shapes = LjShapes,
        content = content,
    )
}
