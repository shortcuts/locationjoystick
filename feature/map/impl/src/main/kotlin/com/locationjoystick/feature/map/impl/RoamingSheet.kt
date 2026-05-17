package com.locationjoystick.feature.map.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.model.SpeedUnit
import kotlin.math.roundToInt

private val SPEED_PROFILES = listOf("walk", "run", "bike")
private val SPEED_PROFILE_LABELS = mapOf("walk" to "Walk", "run" to "Run", "bike" to "Bike")

private const val RADIUS_MIN = 1_000f
private const val RADIUS_MAX = 100_000f
private const val DISTANCE_MIN = 50f
private const val DISTANCE_MAX = 50_000f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoamingSheet(
    draft: RoamingDraft,
    hasCurrentPosition: Boolean,
    speedUnit: SpeedUnit = SpeedUnit.KMH,
    onAction: (MapAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val isMph = speedUnit == SpeedUnit.MPH
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
        ) {
            Text("Roaming", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(16.dp))

            var radiusText by remember(isMph) {
                mutableStateOf(
                    if (isMph) String.format("%.2f", draft.radiusMeters / 1609.344)
                    else draft.radiusMeters.roundToInt().toString(),
                )
            }
            OutlinedTextField(
                value = radiusText,
                onValueChange = { text ->
                    radiusText = text
                    text.toDoubleOrNull()?.let { v ->
                        val meters = if (isMph) v * 1609.344 else v
                        onAction(MapAction.UpdateRoamingRadius(meters.coerceIn(RADIUS_MIN.toDouble(), RADIUS_MAX.toDouble())))
                    }
                },
                label = { Text(if (isMph) "Radius (mi)" else "Radius (m)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            var distanceText by remember(isMph) {
                mutableStateOf(
                    if (isMph) String.format("%.2f", draft.distanceMeters / 1609.344)
                    else draft.distanceMeters.roundToInt().toString(),
                )
            }
            OutlinedTextField(
                value = distanceText,
                onValueChange = { text ->
                    distanceText = text
                    text.toDoubleOrNull()?.let { v ->
                        val meters = if (isMph) v * 1609.344 else v
                        onAction(MapAction.UpdateRoamingDistance(meters.coerceIn(DISTANCE_MIN.toDouble(), DISTANCE_MAX.toDouble())))
                    }
                },
                label = { Text(if (isMph) "Route distance (mi)" else "Route distance (m)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Text("Speed profile", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SPEED_PROFILES.forEach { id ->
                    FilterChip(
                        selected = draft.speedProfileId == id,
                        onClick = { onAction(MapAction.SelectRoamingSpeedProfile(id)) },
                        label = { Text(SPEED_PROFILE_LABELS[id] ?: id) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = draft.followRoads,
                    onCheckedChange = { onAction(MapAction.ToggleRoamingFollowRoads(it)) },
                )
                Text("Follow roads", style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = draft.returnToInitialLocation,
                    onCheckedChange = { onAction(MapAction.ToggleRoamingReturnToStart(it)) },
                )
                Text("Return to start", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onAction(MapAction.StartRoaming) },
                enabled = hasCurrentPosition,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start")
            }

            if (!hasCurrentPosition) {
                Text(
                    "Start location spoofing first to enable roaming",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun RoamingSheetPreview() {
    LjTheme {
        RoamingSheet(
            draft =
                RoamingDraft(
                    radiusMeters = 5_000.0,
                    distanceMeters = 1_000.0,
                    speedProfileId = "walk",
                    followRoads = true,
                    returnToInitialLocation = true,
                ),
            hasCurrentPosition = true,
            onAction = {},
            onDismiss = {},
        )
    }
}
