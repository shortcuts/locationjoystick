package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import kotlin.math.roundToInt

private fun formatJitterDouble(d: Double): String {
    val rounded = (d * 100).roundToInt() / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

@Composable
private fun JitterInput(
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
            v.toDoubleOrNull()?.let {
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
    Text("GPS Jitter", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Adds noise to each location update. Set 0 to disable.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = if (isMph) uiState.jitterIdleRadiusMeters * 3.28084 else uiState.jitterIdleRadiusMeters,
            onValueChange = { onAction(SettingsAction.SetJitterIdleRadius(if (isMph) it / 3.28084 else it)) },
            label = if (isMph) "Idle radius (ft)" else "Idle radius (m)",
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterIdleIntervalSeconds,
            onValueChange = { onAction(SettingsAction.SetJitterIdleIntervalSeconds(it)) },
            label = "Idle interval (s)",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = if (isMph) uiState.jitterMovingRadiusMeters * 3.28084 else uiState.jitterMovingRadiusMeters,
            onValueChange = { onAction(SettingsAction.SetJitterMovingRadius(if (isMph) it / 3.28084 else it)) },
            label = if (isMph) "Moving radius (ft)" else "Moving radius (m)",
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterIntervalSeconds,
            onValueChange = { onAction(SettingsAction.SetJitterIntervalSeconds(it)) },
            label = "Moving interval (s)",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = uiState.jitterSpeedIdleVariationPct.toDouble(),
            onValueChange = { onAction(SettingsAction.SetJitterSpeedIdleVariationPct(it.toInt())) },
            label = "Idle speed variation (%)",
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterSpeedMovingVariationPct.toDouble(),
            onValueChange = { onAction(SettingsAction.SetJitterSpeedMovingVariationPct(it.toInt())) },
            label = "Moving speed variation (%)",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ElevationJitterSection(
    uiState: SettingsUiState,
    elevationControlsEnabled: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Elevation Jitter", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        if (elevationControlsEnabled) {
            "Noise applied to sensor injection each tick. Set 0 to disable."
        } else {
            "Enable Elevation controls in the Floating Widget section to configure these inputs."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = uiState.elevationTiltJitterDegrees.toDouble(),
            onValueChange = { onAction(SettingsAction.SetElevationTiltJitterDegrees(it.toFloat())) },
            label = "Tilt jitter (°)",
            modifier = Modifier.weight(1f),
            enabled = elevationControlsEnabled,
        )
        JitterInput(
            value = uiState.elevationNoiseAmplitudeMs2.toDouble(),
            onValueChange = { onAction(SettingsAction.SetElevationNoiseAmplitudeMs2(it.toFloat())) },
            label = "Accel noise (m/s²)",
            modifier = Modifier.weight(1f),
            enabled = elevationControlsEnabled,
        )
    }
}

@Composable
internal fun GpsRealismSection(
    uiState: SettingsUiState,
    isRooted: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    Text("GPS Realism", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Controls how the spoofed GPS signal behaves. These options add metadata and variation that real GPS chips produce — some apps and games inspect these signals to detect mock providers.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.realismBearingHoldIdle,
        onCheckedChange = { onAction(SettingsAction.SetRealismBearingHoldIdle(it)) },
        title = "Hold bearing when stationary",
        description =
            "Keeps the last known direction when you stop moving instead of snapping to 0° (north). " +
                "Real GPS chips do the same — a sudden reset to north is a common mock-location tell.",
    )
    LjCheckboxRow(
        checked = uiState.realismAltitudeEnabled,
        onCheckedChange = { onAction(SettingsAction.SetRealismAltitudeEnabled(it)) },
        title = "Vary altitude",
        description =
            "Simulates a plausible altitude with small random drift instead of always reporting 0 m. " +
                "A flat zero altitude is an obvious signal that the location is synthetic.",
    )
    LjCheckboxRow(
        checked = uiState.realismWarmupEnabled,
        onCheckedChange = { onAction(SettingsAction.SetRealismWarmupEnabled(it)) },
        title = "GPS warm-up simulation",
        description =
            "Starts each session with degraded accuracy (like a cold GPS fix) that converges to normal over ~30 s. " +
                "Off by default because it temporarily reduces location precision at session start.",
    )
    LjCheckboxRow(
        checked = uiState.realismSatelliteExtrasEnabled,
        onCheckedChange = { onAction(SettingsAction.SetRealismSatelliteExtrasEnabled(it)) },
        title = "Realistic satellite count",
        description =
            "Attaches satellite metadata to each update (7–14 satellites visible, 6–12 in fix) instead of zero. " +
                "Some apps check for zero satellites as a spoofing signal.",
    )
    LjCheckboxRow(
        checked = uiState.realismSuspendedMockingEnabled,
        onCheckedChange = { onAction(SettingsAction.SetRealismSuspendedMockingEnabled(it)) },
        title = "Suspended mocking",
        description =
            "Briefly pauses location updates (~2 s every ~10 s) to mimic real GPS dropouts. " +
                "Off by default because the pauses cause visible position freezes in many apps. " +
                "Automatically skipped during route replay.",
    )
    LjCheckboxRow(
        checked = uiState.realismPedometerMockingEnabled,
        onCheckedChange = { onAction(SettingsAction.SetRealismPedometerMockingEnabled(it)) },
        enabled = isRooted,
        title = "Mock step counter",
        description =
            if (isRooted) {
                "Injects synthetic step counts that match your movement speed. " +
                    "Some apps cross-check GPS movement against pedometer data to detect spoofing. " +
                    "Steps are only injected while walking or running (not while using the bike profile)."
            } else {
                "Injects synthetic step counts that match your movement speed. Requires root access (same permission as Elevation controls)."
            },
    )
}
