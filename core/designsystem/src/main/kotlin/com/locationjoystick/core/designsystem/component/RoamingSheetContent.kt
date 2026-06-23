package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjText
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import kotlin.math.roundToInt

private val SPEED_PROFILES = listOf("walk", "run", "bike")
private val SPEED_PROFILE_LABELS = mapOf("walk" to "Walk", "run" to "Run", "bike" to "Bike")

private const val RADIUS_MIN_METERS = 1_000.0
private const val RADIUS_MAX_METERS = 100_000.0
private const val DISTANCE_MIN_METERS = 50.0
private const val DISTANCE_MAX_METERS = 50_000.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoamingSheetContent(
    draft: RoamingDefaults,
    speedUnit: SpeedUnit,
    hasCurrentPosition: Boolean,
    isSpoofingActive: Boolean,
    hasPreview: Boolean,
    isPreviewLoading: Boolean = false,
    onDraftChange: (RoamingDefaults) -> Unit,
    onGenerate: () -> Unit,
    onStart: () -> Unit,
    onViewOnMap: () -> Unit,
) {
    val isMph = speedUnit == SpeedUnit.MPH

    var radiusText by remember(isMph) {
        mutableStateOf(
            if (isMph) {
                String.format("%.2f", draft.radiusMeters / 1609.344)
            } else {
                draft.radiusMeters.roundToInt().toString()
            },
        )
    }

    var distanceText by remember(isMph) {
        mutableStateOf(
            if (isMph) {
                String.format("%.2f", draft.distanceMeters / 1609.344)
            } else {
                draft.distanceMeters.roundToInt().toString()
            },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
    ) {
        Text("Roaming", style = MaterialTheme.typography.headlineSmall, color = LjText)

        Spacer(Modifier.height(8.dp))

        // "View on map" always visible; greyed out when no preview
        LjTextButton(
            onClick = onViewOnMap,
            enabled = hasPreview,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (!hasPreview) Modifier.alpha(0.4f) else Modifier),
        ) {
            Text("View on map")
        }

        Spacer(Modifier.height(12.dp))

        // Radius + Route distance side by side
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = radiusText,
                onValueChange = { text ->
                    radiusText = text
                    text.toDoubleOrNull()?.let { v ->
                        val meters = if (isMph) v * 1609.344 else v
                        onDraftChange(
                            draft.copy(
                                radiusMeters =
                                    meters.coerceIn(
                                        RADIUS_MIN_METERS,
                                        RADIUS_MAX_METERS,
                                    ),
                            ),
                        )
                    }
                },
                label = { Text(if (isMph) "Radius (mi)" else "Radius (m)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f).padding(end = 4.dp),
            )
            OutlinedTextField(
                value = distanceText,
                onValueChange = { text ->
                    distanceText = text
                    text.toDoubleOrNull()?.let { v ->
                        val meters = if (isMph) v * 1609.344 else v
                        onDraftChange(
                            draft.copy(
                                distanceMeters =
                                    meters.coerceIn(
                                        DISTANCE_MIN_METERS,
                                        DISTANCE_MAX_METERS,
                                    ),
                            ),
                        )
                    }
                },
                label = { Text(if (isMph) "Distance (mi)" else "Distance (m)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Speed profile selector
        Text("Speed profile", style = MaterialTheme.typography.labelLarge, color = LjText)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SPEED_PROFILES.forEachIndexed { index, id ->
                SegmentedButton(
                    selected = draft.speedProfileId == id,
                    onClick = { onDraftChange(draft.copy(speedProfileId = id)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = SPEED_PROFILES.size),
                ) {
                    Text(SPEED_PROFILE_LABELS[id] ?: id)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Follow roads + Return to start side by side
        Row(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Checkbox(
                    checked = draft.followRoads,
                    onCheckedChange = { onDraftChange(draft.copy(followRoads = it)) },
                )
                Text("Follow roads", style = MaterialTheme.typography.bodyMedium, color = LjText)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Checkbox(
                    checked = draft.returnToInitialLocation,
                    onCheckedChange = { onDraftChange(draft.copy(returnToInitialLocation = it)) },
                )
                Text("Return to start", style = MaterialTheme.typography.bodyMedium, color = LjText)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Generate + Start side by side
        Row(modifier = Modifier.fillMaxWidth()) {
            LjOutlinedButton(
                onClick = onGenerate,
                enabled = hasCurrentPosition && !isPreviewLoading,
                modifier = Modifier.weight(1f).padding(end = 4.dp),
            ) {
                if (isPreviewLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Generate")
                }
            }
            LjButton(
                onClick = onStart,
                enabled = hasCurrentPosition && isSpoofingActive && !isPreviewLoading,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            ) {
                Text("Start")
            }
        }

        if (!hasCurrentPosition || !isSpoofingActive) {
            Text(
                "Start location spoofing first to enable roaming",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
