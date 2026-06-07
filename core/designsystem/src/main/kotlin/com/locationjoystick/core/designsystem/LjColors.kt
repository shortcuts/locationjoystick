package com.locationjoystick.core.designsystem

import androidx.compose.material3.darkColorScheme
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
