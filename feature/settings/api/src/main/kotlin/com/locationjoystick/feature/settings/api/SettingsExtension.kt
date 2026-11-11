package com.locationjoystick.feature.settings.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/** A Settings hub card with its own screen. Play has no implementation, so its hub shows no extra card. */
interface SettingsExtension {
    val id: String
    val icon: ImageVector

    @Composable
    fun title(): String

    @Composable
    fun description(): String

    @Composable
    fun Screen(onBack: () -> Unit)
}
