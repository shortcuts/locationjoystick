package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Quiet-utility section label shared by every Settings sub-screen. */
@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

/** Hairline separator between Settings sections. */
@Composable
internal fun SettingsSectionDivider() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
}
