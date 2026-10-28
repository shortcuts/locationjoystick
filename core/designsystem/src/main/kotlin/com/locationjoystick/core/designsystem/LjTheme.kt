package com.locationjoystick.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.locationjoystick.core.model.GuidedPath

val LocalGuidedPath = staticCompositionLocalOf { GuidedPath.DEFAULT }

@Composable
fun LjTheme(
    guidedPath: GuidedPath,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGuidedPath provides guidedPath) {
        MaterialTheme(
            colorScheme = if (darkTheme) LjDarkColorScheme else LjLightColorScheme,
            typography = LjTypography,
            shapes = LjShapes,
            content = content,
        )
    }
}
