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
    Text("Location Randomness", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Adds small random shifts to your fake location, making it look more natural. Set 0 to disable.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text("Position jitter", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = if (isMph) uiState.jitterIdleRadiusMeters * 3.28084 else uiState.jitterIdleRadiusMeters,
            onValueChange = { onAction(SettingsAction.SetJitterIdleRadius(if (isMph) it / 3.28084 else it)) },
            label = if (isMph) "Wobble when still (ft)" else "Wobble when still (m)",
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterIdleIntervalSeconds,
            onValueChange = { onAction(SettingsAction.SetJitterIdleIntervalSeconds(it)) },
            label = "How often when still (sec)",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = if (isMph) uiState.jitterMovingRadiusMeters * 3.28084 else uiState.jitterMovingRadiusMeters,
            onValueChange = { onAction(SettingsAction.SetJitterMovingRadius(if (isMph) it / 3.28084 else it)) },
            label = if (isMph) "Wobble while moving (ft)" else "Wobble while moving (m)",
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterIntervalSeconds,
            onValueChange = { onAction(SettingsAction.SetJitterIntervalSeconds(it)) },
            label = "How often while moving (sec)",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text("Speed variation", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = uiState.jitterSpeedIdleVariationPct.toDouble(),
            onValueChange = { onAction(SettingsAction.SetJitterSpeedIdleVariationPct(it.toInt())) },
            label = "Speed wobble when still (%)",
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterSpeedMovingVariationPct.toDouble(),
            onValueChange = { onAction(SettingsAction.SetJitterSpeedMovingVariationPct(it.toInt())) },
            label = "Speed wobble while moving (%)",
            modifier = Modifier.weight(1f),
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
        "Controls how the fake GPS signal behaves. These options add metadata and variation that real GPS chips produce — some apps and games inspect these signals to detect fake locations.",
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
            "Starts each session with slightly inaccurate readings that improve over ~30 seconds, " +
                "like a real GPS that takes time to lock on. " +
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
        title = "Simulate signal dropouts",
        description =
            "Briefly pauses the fake location signal every ~10 seconds, like a real GPS dropping signal momentarily. " +
                "Off by default — the pauses cause visible freezes in most apps. " +
                "Skipped automatically during route replay.",
    )
    LjCheckboxRow(
        checked = uiState.realismPedometerMockingEnabled,
        onCheckedChange = { onAction(SettingsAction.SetRealismPedometerMockingEnabled(it)) },
        enabled = isRooted,
        title = "Fake step counter",
        description =
            if (isRooted) {
                "Fakes step counts that match your movement speed. " +
                    "Some apps cross-check GPS movement against step data to detect fake locations. " +
                    "Steps are only faked while walking or running (not while using the bike profile)."
            } else {
                "Fakes step counts that match your movement speed. Requires root access."
            },
    )
}
