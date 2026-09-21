package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.data.DebugStats
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSuccess
import com.locationjoystick.core.designsystem.LjText
import com.locationjoystick.feature.widget.impl.R

internal fun formatBearingText(stats: DebugStats): String = if (stats.hasBearing) "%.0f°".format(stats.bearing) else "—"

@Composable
internal fun DebugStatsPanel(stats: DebugStats) {
    Column(
        modifier =
            Modifier
                .padding(4.dp)
                .shadow(elevation = 8.dp, shape = MaterialTheme.shapes.small)
                .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.small)
                .padding(8.dp),
    ) {
        val tickHz = if (stats.tickIntervalMs > 0) 1000f / stats.tickIntervalMs else 0f
        Text(
            stringResource(R.string.widget_panel_content_2f_6f).format(stats.latitude, stats.longitude),
            color = LjText,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            stringResource(R.string.widget_panel_content_speed_2f_m_s_alt_ellipsoidal_2f_m).format(stats.speedMs, stats.altitudeMeters),
            color = LjText,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            stringResource(
                R.string.widget_panel_content_acc_1f_m_bearing_s_1f_hz,
            ).format(stats.accuracyMeters, formatBearingText(stats), tickHz),
            color = LjText,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun AltitudeOverrideInput(
    prefillMeters: Double,
    onConfirm: (Double) -> Unit,
) {
    // Captured once when this composable enters composition (i.e. on expand), not re-read on
    // every recomposition — the live altitude changes every tick while spoofing and would
    // otherwise stomp on what the user is typing.
    var value by remember { mutableStateOf(prefillMeters.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.width(100.dp),
            singleLine = true,
            label = { Text(stringResource(R.string.widget_panel_altitude_m)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { value.toDoubleOrNull()?.let(onConfirm) }),
        )
        IconButton(onClick = { value.toDoubleOrNull()?.let(onConfirm) }) {
            Icon(LjIcons.Check, contentDescription = stringResource(R.string.widget_panel_confirm_altitude_cd), tint = LjSuccess)
        }
    }
}
