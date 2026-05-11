package com.locationjoystick.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val LjBg     = Color(0xFF1E1E24)
val LjText   = Color(0xFFF7EBE8)
val LjAccent = Color(0xFFF79D5C)
val LjError  = Color(0xFFEF4444)

val LjDarkColorScheme = darkColorScheme(
    primary              = LjAccent,
    onPrimary            = LjBg,
    primaryContainer     = LjBg,
    onPrimaryContainer   = LjText,
    secondary            = LjAccent,
    onSecondary          = LjBg,
    secondaryContainer   = LjBg,
    onSecondaryContainer = LjText,
    tertiary             = LjAccent,
    onTertiary           = LjBg,
    tertiaryContainer    = LjBg,
    onTertiaryContainer  = LjText,
    error                = LjError,
    onError              = LjText,
    errorContainer       = LjBg,
    onErrorContainer     = LjText,
    background           = LjBg,
    onBackground         = LjText,
    surface              = LjBg,
    onSurface            = LjText,
    surfaceVariant       = LjBg,
    onSurfaceVariant     = LjText,
    outline              = LjAccent,
    outlineVariant       = LjBg,
    inverseSurface       = LjText,
    inverseOnSurface     = LjBg,
    inversePrimary       = LjAccent,
    scrim                = Color(0x80000000),
)
