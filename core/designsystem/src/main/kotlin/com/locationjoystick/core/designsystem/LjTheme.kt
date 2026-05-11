package com.locationjoystick.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun LjTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LjDarkColorScheme,
        typography = LjTypography,
        shapes = LjShapes,
        content = content,
    )
}
