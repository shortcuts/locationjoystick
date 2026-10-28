package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.component.PasteCoordinatesForm
import com.locationjoystick.core.designsystem.component.RoamingSheetContent
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.RouteStartConfig
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.toConfig
import kotlinx.coroutines.launch

@Composable
internal fun BoxScope.OverlayRoamingSheet(
    currentPosition: LatLng?,
    roamingDefaults: RoamingDefaults,
    speedUnit: SpeedUnit,
    mockLocationState: MockLocationState,
    routePlaying: Boolean,
    hasPreview: Boolean,
    onGeneratePreviewRoute: suspend (RoamingConfig) -> List<LatLng>?,
    onPreviewGenerated: (List<LatLng>?) -> Unit,
    onStart: (RoamingDefaults) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(roamingDefaults) { mutableStateOf(roamingDefaults) }
    var isPreviewLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Scrim — tapping outside the sheet dismisses it. ModalBottomSheet is not used because it
    // creates a Dialog internally, which requires an Activity window token unavailable in a
    // service overlay.
    Box(modifier = Modifier.fillMaxSize().clickable { onDismiss() })
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .background(MaterialTheme.colorScheme.surface, overlaySheetShape())
                .clickable {},
    ) {
        // Drag handle — matches ModalBottomSheet visual
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = LjSpacing.sm)
                    .width(32.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp),
                    ),
        )
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(modifier = Modifier.fillMaxSize().padding(top = 28.dp)) {
                RoamingSheetContent(
                    draft = draft,
                    speedUnit = speedUnit,
                    hasCurrentPosition = currentPosition != null,
                    isSpoofingActive =
                        mockLocationState == MockLocationState.RUNNING ||
                            mockLocationState == MockLocationState.PAUSED,
                    hasPreview = hasPreview,
                    isPreviewLoading = isPreviewLoading,
                    routePlaying = routePlaying,
                    onDraftChange = { draft = it },
                    onGenerate = { kind ->
                        val next = draft.copy(kind = kind)
                        draft = next
                        scope.launch {
                            val pos = currentPosition ?: return@launch
                            isPreviewLoading = true
                            try {
                                onPreviewGenerated(onGeneratePreviewRoute(next.toConfig(pos)))
                            } finally {
                                isPreviewLoading = false
                            }
                        }
                    },
                    onStart = { kind -> onStart(draft.copy(kind = kind)) },
                    onViewOnMap = { onDismiss() },
                )
            }
        }
    }
}

@Composable
internal fun BoxScope.OverlayPasteCoordinatesSheet(
    onDismiss: () -> Unit,
    onTeleport: (LatLng) -> Unit,
    onWalk: (LatLng) -> Unit,
    onWalkViaRoads: (LatLng) -> Unit,
    onSaveFavorite: (name: String, position: LatLng) -> Unit,
    onSaveRoute: (name: String, points: List<LatLng>) -> Unit,
    onStartRoute: (points: List<LatLng>, config: RouteStartConfig) -> Unit,
    hideTeleportFeatures: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxSize().clickable { onDismiss() })
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, overlaySheetShape())
                .clickable {}
                .imePadding(),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = LjSpacing.sm)
                    .width(32.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp),
                    ),
        )
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = LjSpacing.sm)) {
                PasteCoordinatesForm(
                    onDismiss = onDismiss,
                    onTeleport = onTeleport,
                    onWalk = onWalk,
                    onWalkViaRoads = onWalkViaRoads,
                    onSaveFavorite = onSaveFavorite,
                    onSaveRoute = onSaveRoute,
                    onStartRoute = onStartRoute,
                    hideTeleportFeatures = hideTeleportFeatures,
                )
            }
        }
    }
}
