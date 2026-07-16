package com.locationjoystick.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LjBg = Color(0xFF1E1E24)
val LjSurface = Color(0xFF252530)
val LjSurfaceVariant = Color(0xFF2D2D3A)
val LjText = Color(0xFFF7EBE8)
val LjTextSecondary = Color(0xFFB0A8B4)
val LjAccent = Color(0xFFF79D5C)
val LjError = Color(0xFFEF4444)
val LjErrorContainer = Color(0xFF3D1A1A)
val LjSuccess = Color(0xFF4CAF50)
val LjInactive = Color(0xFF757575)
val LjWarning = Color(0xFFF59E0B)
val LjWarningContainer = Color(0xFF451A03)

// Light theme — high-contrast variant for sunny/outdoor readability.
val LjLightBg = Color(0xFFFAF7F5)
val LjLightSurface = Color(0xFFFFFFFF)
val LjLightSurfaceVariant = Color(0xFFF0E9E4)
val LjLightText = Color(0xFF231E1B)
val LjLightTextSecondary = Color(0xFF5C5259)
val LjLightAccent = Color(0xFFB2531A)
val LjLightOutlineVariant = Color(0xFFDDD3CB)

object LjMapColors {
    val ActiveButton = Color(0xFF43A047)
    val PositionBlue = Color(0xFF1976D2)
    val RouteOrange = Color(0xFFFF9800)
    val PointStroke = Color(0xFFFFFFFF)
    val PendingTapGreen = Color(0xFF4CAF50)
}

val LjDarkColorScheme =
    darkColorScheme(
        primary = LjAccent,
        onPrimary = LjBg,
        primaryContainer = Color(0xFF3D2E1E),
        onPrimaryContainer = LjAccent,
        secondary = LjAccent,
        onSecondary = LjBg,
        secondaryContainer = Color(0xFF3D2E1E),
        onSecondaryContainer = LjAccent,
        tertiary = LjAccent,
        onTertiary = LjBg,
        tertiaryContainer = Color(0xFF2A2E3D),
        onTertiaryContainer = LjAccent,
        error = LjError,
        onError = LjText,
        errorContainer = LjErrorContainer,
        onErrorContainer = LjError,
        background = LjBg,
        onBackground = LjText,
        surface = LjSurface,
        onSurface = LjText,
        surfaceVariant = LjSurfaceVariant,
        onSurfaceVariant = LjTextSecondary,
        outline = LjAccent,
        outlineVariant = Color(0xFF3A3A48),
        inverseSurface = LjText,
        inverseOnSurface = LjBg,
        inversePrimary = LjAccent,
        scrim = Color(0x80000000),
    )

val LjLightColorScheme =
    lightColorScheme(
        primary = LjLightAccent,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE0C2),
        onPrimaryContainer = LjLightAccent,
        secondary = LjLightAccent,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE0C2),
        onSecondaryContainer = LjLightAccent,
        tertiary = LjLightAccent,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF3E5D8),
        onTertiaryContainer = LjLightAccent,
        error = LjError,
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = LjError,
        background = LjLightBg,
        onBackground = LjLightText,
        surface = LjLightSurface,
        onSurface = LjLightText,
        surfaceVariant = LjLightSurfaceVariant,
        onSurfaceVariant = LjLightTextSecondary,
        outline = LjLightAccent,
        outlineVariant = LjLightOutlineVariant,
        inverseSurface = LjLightText,
        inverseOnSurface = LjLightBg,
        inversePrimary = LjAccent,
        scrim = Color(0x80000000),
    )
