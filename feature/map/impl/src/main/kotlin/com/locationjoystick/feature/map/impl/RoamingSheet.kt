package com.locationjoystick.feature.map.impl

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.component.RoamingSheetContent
import com.locationjoystick.core.designsystem.component.rememberLjSheetState
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoamingSheet(
    draft: RoamingDefaults,
    hasCurrentPosition: Boolean,
    isSpoofingActive: Boolean = true,
    speedUnit: SpeedUnit = SpeedUnit.KMH,
    hasPreview: Boolean = false,
    isPreviewLoading: Boolean = false,
    routePlaying: Boolean = false,
    onAction: (MapAction) -> Unit,
    onGeneratePreview: () -> Unit = {},
    onMinimize: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (hasPreview) onMinimize() else onDismiss() },
        sheetState = rememberLjSheetState(),
        modifier = Modifier.fillMaxHeight(0.8f),
    ) {
        RoamingSheetContent(
            draft = draft,
            speedUnit = speedUnit,
            hasCurrentPosition = hasCurrentPosition,
            isSpoofingActive = isSpoofingActive,
            hasPreview = hasPreview,
            isPreviewLoading = isPreviewLoading,
            routePlaying = routePlaying,
            onDraftChange = { updated ->
                if (updated.kind != draft.kind) {
                    onAction(MapAction.UpdateRoamingKind(updated.kind))
                }
                if (updated.radiusMeters != draft.radiusMeters) {
                    onAction(MapAction.UpdateRoamingRadius(updated.radiusMeters))
                }
                if (updated.distanceMeters != draft.distanceMeters) {
                    onAction(MapAction.UpdateRoamingDistance(updated.distanceMeters))
                }
                if (updated.speedProfileId != draft.speedProfileId) {
                    onAction(MapAction.SelectRoamingSpeedProfile(updated.speedProfileId))
                }
                if (updated.plantingSpeedProfileId != draft.plantingSpeedProfileId) {
                    onAction(MapAction.SelectPlantingSpeedProfile(updated.plantingSpeedProfileId))
                }
                if (updated.followRoads != draft.followRoads) {
                    onAction(MapAction.ToggleRoamingFollowRoads(updated.followRoads))
                }
                if (updated.returnToInitialLocation != draft.returnToInitialLocation) {
                    onAction(MapAction.ToggleRoamingReturnToStart(updated.returnToInitialLocation))
                }
                if (updated.plantingStartRadiusMeters != draft.plantingStartRadiusMeters) {
                    onAction(MapAction.UpdatePlantingStartRadius(updated.plantingStartRadiusMeters))
                }
                if (updated.plantingEndRadiusMeters != draft.plantingEndRadiusMeters) {
                    onAction(MapAction.UpdatePlantingEndRadius(updated.plantingEndRadiusMeters))
                }
                if (updated.plantingInfiniteLoops != draft.plantingInfiniteLoops) {
                    onAction(MapAction.TogglePlantingInfiniteLoops(updated.plantingInfiniteLoops))
                }
                if (updated.plantingLoopCount != draft.plantingLoopCount) {
                    onAction(MapAction.UpdatePlantingLoopCount(updated.plantingLoopCount))
                }
            },
            onGenerate = { kind ->
                onAction(MapAction.UpdateRoamingKind(kind))
                onGeneratePreview()
            },
            onStart = { kind ->
                onAction(MapAction.UpdateRoamingKind(kind))
                onAction(MapAction.StartRoaming)
            },
            onViewOnMap = onMinimize,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RoamingSheetPreview() {
    LjTheme {
        RoamingSheet(
            draft =
                RoamingDefaults(
                    radiusMeters = 5_000.0,
                    distanceMeters = 1_000.0,
                    speedProfileId = AppConstants.ProfileConstants.PROFILE_ID_WALK,
                    followRoads = true,
                    returnToInitialLocation = true,
                ),
            hasCurrentPosition = true,
            isSpoofingActive = true,
            onAction = {},
            onDismiss = {},
        )
    }
}
