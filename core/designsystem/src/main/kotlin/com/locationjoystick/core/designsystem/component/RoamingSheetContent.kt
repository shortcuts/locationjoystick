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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.toLocaleDoubleOrNull
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.LjText
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.RoamingKind
import com.locationjoystick.core.model.SpeedUnit
import kotlin.math.roundToInt

private val SPEED_PROFILES = listOf("walk", "run", "bike")

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
    showViewOnMap: Boolean = true,
    routePlaying: Boolean = false,
    onDraftChange: (RoamingDefaults) -> Unit,
    onGenerate: (RoamingKind) -> Unit,
    onStart: (RoamingKind) -> Unit,
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

    var startRadiusText by remember {
        mutableStateOf(draft.plantingStartRadiusMeters.roundToInt().toString())
    }
    var endRadiusText by remember {
        mutableStateOf(draft.plantingEndRadiusMeters.roundToInt().toString())
    }
    var loopCountText by remember {
        mutableStateOf(draft.plantingLoopCount.toString())
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LjSpacing.md)
                .padding(bottom = LjSpacing.lg),
    ) {
        Text(stringResource(R.string.roaming_sheet_roaming), style = MaterialTheme.typography.headlineSmall, color = LjText)

        Spacer(Modifier.height(LjSpacing.sm))

        if (showViewOnMap) {
            LjTextButton(
                onClick = onViewOnMap,
                enabled = hasPreview,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (!hasPreview) Modifier.alpha(0.4f) else Modifier),
            ) {
                Text(stringResource(R.string.roaming_sheet_view_on_map))
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = draft.kind == RoamingKind.PLANTING,
                onCheckedChange = {
                    onDraftChange(draft.copy(kind = if (it) RoamingKind.PLANTING else RoamingKind.WALK_AROUND))
                },
            )
            Text(
                stringResource(R.string.roaming_sheet_content_planting_mode),
                style = MaterialTheme.typography.bodyMedium,
                color = LjText,
            )
        }
        Spacer(Modifier.height(12.dp))

        if (draft.kind == RoamingKind.WALK_AROUND) {
            Text(stringResource(R.string.roaming_sheet_content_walk_around_the_block), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = { text ->
                        radiusText = text
                        text.toLocaleDoubleOrNull()?.let { v ->
                            val meters = if (isMph) v * 1609.344 else v
                            onDraftChange(
                                draft.copy(
                                    radiusMeters = meters.coerceIn(RADIUS_MIN_METERS, RADIUS_MAX_METERS),
                                ),
                            )
                        }
                    },
                    label = {
                        Text(
                            if (isMph) {
                                stringResource(R.string.roaming_sheet_radius_mi)
                            } else {
                                stringResource(R.string.roaming_sheet_radius_m)
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(end = LjSpacing.xs),
                )
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { text ->
                        distanceText = text
                        text.toLocaleDoubleOrNull()?.let { v ->
                            val meters = if (isMph) v * 1609.344 else v
                            onDraftChange(
                                draft.copy(
                                    distanceMeters = meters.coerceIn(DISTANCE_MIN_METERS, DISTANCE_MAX_METERS),
                                ),
                            )
                        }
                    },
                    label = {
                        Text(
                            if (isMph) {
                                stringResource(R.string.roaming_sheet_distance_mi)
                            } else {
                                stringResource(R.string.roaming_sheet_distance_m)
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(start = LjSpacing.xs),
                )
            }

            Spacer(Modifier.height(12.dp))
            RoamingSpeedProfileRow(
                selectedId = draft.speedProfileId,
                onSelect = { onDraftChange(draft.copy(speedProfileId = it)) },
            )
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Checkbox(
                        checked = draft.followRoads,
                        onCheckedChange = { onDraftChange(draft.copy(followRoads = it)) },
                    )
                    Text(stringResource(R.string.roaming_sheet_follow_roads), style = MaterialTheme.typography.bodyMedium, color = LjText)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Checkbox(
                        checked = draft.returnToInitialLocation,
                        onCheckedChange = { onDraftChange(draft.copy(returnToInitialLocation = it)) },
                    )
                    Text(stringResource(R.string.roaming_sheet_return_to_start), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Text(stringResource(R.string.roaming_sheet_content_planting), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.roaming_spiral_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = startRadiusText,
                    onValueChange = { text ->
                        startRadiusText = text
                        text.toLocaleDoubleOrNull()?.let { v ->
                            onDraftChange(
                                draft.copy(
                                    plantingStartRadiusMeters =
                                        v.coerceIn(
                                            AppConstants.RoamingConstants.ROAMING_MIN_RADIUS_METERS,
                                            AppConstants.RoamingConstants.PLANTING_MAX_RADIUS_METERS,
                                        ),
                                ),
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.roaming_sheet_content_starting_radius_m)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                )
                OutlinedTextField(
                    value = endRadiusText,
                    onValueChange = { text ->
                        endRadiusText = text
                        text.toLocaleDoubleOrNull()?.let { v ->
                            onDraftChange(
                                draft.copy(
                                    plantingEndRadiusMeters =
                                        v.coerceIn(
                                            AppConstants.RoamingConstants.ROAMING_MIN_RADIUS_METERS,
                                            AppConstants.RoamingConstants.PLANTING_MAX_RADIUS_METERS,
                                        ),
                                ),
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.roaming_sheet_content_ending_radius_m)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            RoamingSpeedProfileRow(
                selectedId = draft.plantingSpeedProfileId,
                onSelect = { onDraftChange(draft.copy(plantingSpeedProfileId = it)) },
            )
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = draft.plantingInfiniteLoops,
                    onCheckedChange = { onDraftChange(draft.copy(plantingInfiniteLoops = it)) },
                )
                Text(stringResource(R.string.roaming_sheet_content_infinite_loop), style = MaterialTheme.typography.bodyMedium)
            }

            if (!draft.plantingInfiniteLoops) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = loopCountText,
                    onValueChange = { text ->
                        loopCountText = text
                        text.toIntOrNull()?.let { count ->
                            onDraftChange(
                                draft.copy(
                                    plantingLoopCount =
                                        count.coerceIn(
                                            1,
                                            AppConstants.RoamingConstants.PLANTING_MAX_LOOP_COUNT,
                                        ),
                                ),
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.roaming_sheet_content_number_of_loops)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        RoamingGenerateStartRow(
            kind = draft.kind,
            hasCurrentPosition = hasCurrentPosition,
            isSpoofingActive = isSpoofingActive,
            isPreviewLoading = isPreviewLoading,
            routePlaying = routePlaying,
            onGenerate = onGenerate,
            onStart = onStart,
        )

        if (routePlaying) {
            Text(
                stringResource(R.string.roaming_sheet_content_pause_or_stop_the_playing_route_first_to_start_roaming),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (!hasCurrentPosition || !isSpoofingActive) {
            Text(
                stringResource(R.string.roaming_sheet_start_location_spoofing_first_to_enable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = LjSpacing.xs),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoamingSpeedProfileRow(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Text(stringResource(R.string.roaming_sheet_content_speed_profile), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(4.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SPEED_PROFILES.forEachIndexed { index, id ->
            SegmentedButton(
                selected = selectedId == id,
                onClick = { onSelect(id) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = SPEED_PROFILES.size),
            ) {
                Text(speedProfileLabel(id))
            }
        }
    }
}

@Composable
private fun RoamingGenerateStartRow(
    kind: RoamingKind,
    hasCurrentPosition: Boolean,
    isSpoofingActive: Boolean,
    isPreviewLoading: Boolean,
    routePlaying: Boolean,
    onGenerate: (RoamingKind) -> Unit,
    onStart: (RoamingKind) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        LjOutlinedButton(
            onClick = { onGenerate(kind) },
            enabled = hasCurrentPosition && !isPreviewLoading,
            modifier = Modifier.weight(1f).padding(end = 4.dp),
        ) {
            if (isPreviewLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.roaming_sheet_generate))
            }
        }
        LjButton(
            onClick = { onStart(kind) },
            enabled = hasCurrentPosition && isSpoofingActive && !isPreviewLoading && !routePlaying,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        ) {
            Text(stringResource(R.string.common_start))
        }
    }
}
