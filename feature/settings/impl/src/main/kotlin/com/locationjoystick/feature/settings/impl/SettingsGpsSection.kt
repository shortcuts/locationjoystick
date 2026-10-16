package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.util.toLocaleDoubleOrNull
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjSelectionMode
import com.locationjoystick.core.designsystem.component.LjSelectionRow
import com.locationjoystick.feature.settings.impl.R
import kotlin.math.roundToInt

private fun formatJitterDouble(d: Double): String {
    val rounded = (d * 100).roundToInt() / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

@Composable
internal fun JitterInput(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
) {
    var localValue by remember { mutableStateOf(formatJitterDouble(value)) }
    var lastSentValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (value != lastSentValue) {
            localValue = formatJitterDouble(value)
            lastSentValue = value
        }
    }

    OutlinedTextField(
        value = localValue,
        onValueChange = { v ->
            localValue = v
            v.toLocaleDoubleOrNull()?.let {
                onValueChange(it)
                lastSentValue = it
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun JitterInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
) {
    var localValue by remember { mutableStateOf(value.toString()) }
    var lastSentValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (value != lastSentValue) {
            localValue = value.toString()
            lastSentValue = value
        }
    }

    OutlinedTextField(
        value = localValue,
        onValueChange = { v ->
            localValue = v
            v.toIntOrNull()?.let {
                onValueChange(it)
                lastSentValue = it
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
internal fun GpsJitterSection(
    uiState: SettingsUiState,
    isMph: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_gps_section_location_randomness))
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_gps_section_adds_small_random_shifts_to_your),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(stringResource(R.string.settings_gps_section_position_jitter), style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = if (isMph) uiState.jitterIdleRadiusMeters * 3.28084 else uiState.jitterIdleRadiusMeters,
            onValueChange = { onAction(SettingsAction.SetJitterIdleRadius(if (isMph) it / 3.28084 else it)) },
            label =
                if (isMph) {
                    stringResource(R.string.settings_gps_section_wobble_when_still_ft)
                } else {
                    stringResource(R.string.settings_gps_section_wobble_when_still_m)
                },
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = if (isMph) uiState.jitterMovingRadiusMeters * 3.28084 else uiState.jitterMovingRadiusMeters,
            onValueChange = { onAction(SettingsAction.SetJitterMovingRadius(if (isMph) it / 3.28084 else it)) },
            label =
                if (isMph) {
                    stringResource(R.string.settings_gps_section_wobble_while_moving_ft)
                } else {
                    stringResource(R.string.settings_gps_section_wobble_while_moving_m)
                },
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    JitterInput(
        value = uiState.jitterMaxStepMeters,
        onValueChange = { onAction(SettingsAction.SetJitterMaxStepMeters(it)) },
        label = stringResource(R.string.settings_gps_section_max_step_per_tick_m),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(stringResource(R.string.settings_gps_section_speed_variation), style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = uiState.jitterSpeedIdleVariationPct.toDouble(),
            onValueChange = { onAction(SettingsAction.SetJitterSpeedIdleVariationPct(it.toInt())) },
            label = stringResource(R.string.settings_gps_section_speed_wobble_when_still_pct),
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterSpeedMovingVariationPct.toDouble(),
            onValueChange = { onAction(SettingsAction.SetJitterSpeedMovingVariationPct(it.toInt())) },
            label = stringResource(R.string.settings_gps_section_speed_wobble_while_moving_pct),
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    JitterInput(
        value = uiState.jitterSpeedIdleWobbleProbabilityPct,
        onValueChange = { onAction(SettingsAction.SetJitterSpeedIdleWobbleProbabilityPct(it)) },
        label = stringResource(R.string.settings_gps_section_idle_wobble_frequency_pct),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun GpsRealismSection(
    uiState: SettingsUiState,
    isMph: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_gps_section_gps_realism))
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_gps_section_controls_how_the_fake_gps_signal),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.realismBearingHoldIdle,
        onCheckedChange = { onAction(SettingsAction.SetRealismBearingHoldIdle(it)) },
        title = stringResource(R.string.settings_gps_section_hold_bearing_when_stationary),
        description = stringResource(R.string.settings_gps_section_hold_bearing_when_stationary_desc),
    )
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.realismAltitudeEnabled,
        onCheckedChange = { onAction(SettingsAction.SetRealismAltitudeEnabled(it)) },
        title = stringResource(R.string.settings_gps_section_vary_altitude),
        description = stringResource(R.string.settings_gps_section_vary_altitude_desc),
    )
    if (uiState.realismAltitudeEnabled) {
        JitterInput(
            value = if (isMph) uiState.altitudeJitterRadiusMeters * 3.28084 else uiState.altitudeJitterRadiusMeters,
            onValueChange = { onAction(SettingsAction.SetAltitudeJitterRadius(if (isMph) it / 3.28084 else it)) },
            label =
                if (isMph) {
                    stringResource(R.string.settings_gps_section_vary_altitude_ft)
                } else {
                    stringResource(R.string.settings_gps_section_vary_altitude_m)
                },
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.realismRealElevationEnabled,
        onCheckedChange = { onAction(SettingsAction.SetRealismRealElevationEnabled(it)) },
        title = stringResource(R.string.settings_gps_section_use_real_world_elevation),
        description = stringResource(R.string.settings_gps_section_use_real_world_elevation_desc),
    )
    Spacer(modifier = Modifier.height(4.dp))
    LjButton(
        onClick = { onAction(SettingsAction.ResetAltitudeOverride) },
        enabled = uiState.hasAltitudeOverride,
    ) {
        Text(stringResource(R.string.settings_gps_section_reset_elevation_override))
    }
    Text(
        stringResource(R.string.settings_gps_section_clears_a_manually_set_altitude_floating),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.realismWarmupEnabled,
        onCheckedChange = { onAction(SettingsAction.SetRealismWarmupEnabled(it)) },
        title = stringResource(R.string.settings_gps_section_gps_warm_up_simulation),
        description = stringResource(R.string.settings_gps_section_gps_warm_up_simulation_desc),
    )
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.realismSatelliteExtrasEnabled,
        onCheckedChange = { onAction(SettingsAction.SetRealismSatelliteExtrasEnabled(it)) },
        title = stringResource(R.string.settings_gps_section_realistic_satellite_count),
        description = stringResource(R.string.settings_gps_section_realistic_satellite_count_desc),
    )
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.realismSuspendedMockingEnabled,
        onCheckedChange = { onAction(SettingsAction.SetRealismSuspendedMockingEnabled(it)) },
        title = stringResource(R.string.settings_gps_section_simulate_signal_dropouts),
        description = stringResource(R.string.settings_gps_section_simulate_signal_dropouts_desc),
    )
}
