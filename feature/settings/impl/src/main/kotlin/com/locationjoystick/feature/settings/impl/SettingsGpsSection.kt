package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    onSetJitterIdleRadius: (Double) -> Unit,
    onSetJitterMovingRadius: (Double) -> Unit,
    onSetJitterIntervalSeconds: (Int) -> Unit,
    onSetJitterIdleIntervalSeconds: (Int) -> Unit,
    onSetJitterSpeedIdleVariationPct: (Int) -> Unit,
    onSetJitterSpeedMovingVariationPct: (Int) -> Unit,
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
            onValueChange = { onSetJitterIdleRadius(if (isMph) it / 3.28084 else it) },
            label = if (isMph) "Idle radius (ft)" else "Idle radius (m)",
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterIdleIntervalSeconds,
            onValueChange = { onSetJitterIdleIntervalSeconds(it) },
            label = "Idle interval (s)",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = if (isMph) uiState.jitterMovingRadiusMeters * 3.28084 else uiState.jitterMovingRadiusMeters,
            onValueChange = { onSetJitterMovingRadius(if (isMph) it / 3.28084 else it) },
            label = if (isMph) "Moving radius (ft)" else "Moving radius (m)",
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterIntervalSeconds,
            onValueChange = { onSetJitterIntervalSeconds(it) },
            label = "Moving interval (s)",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = uiState.jitterSpeedIdleVariationPct.toDouble(),
            onValueChange = { onSetJitterSpeedIdleVariationPct(it.toInt()) },
            label = "Idle speed variation (%)",
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterSpeedMovingVariationPct.toDouble(),
            onValueChange = { onSetJitterSpeedMovingVariationPct(it.toInt()) },
            label = "Moving speed variation (%)",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ElevationJitterSection(
    uiState: SettingsUiState,
    elevationControlsEnabled: Boolean,
    onSetTiltJitterDegrees: (Float) -> Unit,
    onSetNoiseAmplitudeMs2: (Float) -> Unit,
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
            onValueChange = { onSetTiltJitterDegrees(it.toFloat()) },
            label = "Tilt jitter (°)",
            modifier = Modifier.weight(1f),
            enabled = elevationControlsEnabled,
        )
        JitterInput(
            value = uiState.elevationNoiseAmplitudeMs2.toDouble(),
            onValueChange = { onSetNoiseAmplitudeMs2(it.toFloat()) },
            label = "Accel noise (m/s²)",
            modifier = Modifier.weight(1f),
            enabled = elevationControlsEnabled,
        )
    }
}

@Composable
internal fun GpsRealismSection(
    uiState: SettingsUiState,
    onSetRealismBearingHoldIdle: (Boolean) -> Unit,
    onSetRealismAltitudeEnabled: (Boolean) -> Unit,
    onSetRealismWarmupEnabled: (Boolean) -> Unit,
    onSetRealismSatelliteExtrasEnabled: (Boolean) -> Unit,
    onSetRealismSuspendedMockingEnabled: (Boolean) -> Unit,
) {
    Text("GPS Realism", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Controls how the spoofed GPS signal behaves. These options add metadata and variation that real GPS chips produce — some apps and games inspect these signals to detect mock providers.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    SettingsCheckboxRow(
        checked = uiState.realismBearingHoldIdle,
        onCheckedChange = onSetRealismBearingHoldIdle,
        title = "Hold bearing when stationary",
        description =
            "Keeps the last known direction when you stop moving instead of snapping to 0° (north). " +
                "Real GPS chips do the same — a sudden reset to north is a common mock-location tell.",
    )
    SettingsCheckboxRow(
        checked = uiState.realismAltitudeEnabled,
        onCheckedChange = onSetRealismAltitudeEnabled,
        title = "Vary altitude",
        description =
            "Simulates a plausible altitude with small random drift instead of always reporting 0 m. " +
                "A flat zero altitude is an obvious signal that the location is synthetic.",
    )
    SettingsCheckboxRow(
        checked = uiState.realismWarmupEnabled,
        onCheckedChange = onSetRealismWarmupEnabled,
        title = "GPS warm-up simulation",
        description =
            "Starts each session with degraded accuracy (like a cold GPS fix) that converges to normal over ~30 s. " +
                "Off by default because it temporarily reduces location precision at session start.",
    )
    SettingsCheckboxRow(
        checked = uiState.realismSatelliteExtrasEnabled,
        onCheckedChange = onSetRealismSatelliteExtrasEnabled,
        title = "Realistic satellite count",
        description =
            "Attaches satellite metadata to each update (7–14 satellites visible, 6–12 in fix) instead of zero. " +
                "Some apps check for zero satellites as a spoofing signal.",
    )
    SettingsCheckboxRow(
        checked = uiState.realismSuspendedMockingEnabled,
        onCheckedChange = onSetRealismSuspendedMockingEnabled,
        title = "Suspended mocking",
        description =
            "Briefly pauses location updates (~2 s every ~10 s) to mimic real GPS dropouts. " +
                "Off by default because the pauses cause visible position freezes in many apps. " +
                "Automatically skipped during route replay.",
    )
}

@Composable
internal fun SettingsCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    descriptionColor: androidx.compose.ui.graphics.Color? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                title,
                color =
                    if (enabled) {
                        androidx.compose.ui.graphics.Color.Unspecified
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.38f,
                        )
                    },
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = descriptionColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
