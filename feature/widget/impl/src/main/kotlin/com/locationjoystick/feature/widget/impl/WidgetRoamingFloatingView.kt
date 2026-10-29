package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.component.RoamingSheetContent
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.feature.widget.impl.R
import kotlinx.coroutines.launch

@Composable
internal fun RoamingFloatingView(
    draft: RoamingDefaults,
    speedUnit: SpeedUnit,
    currentPosition: LatLng?,
    tileSource: MapTileSource,
    hasCurrentPosition: Boolean,
    isSpoofingActive: Boolean,
    routePlaying: Boolean = false,
    onDismiss: () -> Unit,
    onDraftChange: (RoamingDefaults) -> Unit,
    onGenerate: suspend (RoamingDefaults) -> List<LatLng>?,
    onStart: (RoamingDefaults, List<LatLng>?) -> Unit,
    // Robolectric cannot load MapLibre's native library, so tests replace the map.
    previewMap: @Composable (List<LatLng>) -> Unit = { points ->
        RoamingPreviewMap(
            points = points,
            currentPosition = currentPosition,
            tileSource = tileSource,
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium),
        )
    },
) {
    var preview by remember { mutableStateOf<List<LatLng>?>(null) }
    var isPreviewLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FloatingPickerShell(
        title = stringResource(R.string.widget_panel_content_roaming),
        onDismiss = onDismiss,
        hasBack = false,
        onBack = onDismiss,
    ) {
        preview?.let { previewMap(it) }
        Column(Modifier.weight(1f)) {
            RoamingSheetContent(
                draft = draft,
                speedUnit = speedUnit,
                hasCurrentPosition = hasCurrentPosition,
                isSpoofingActive = isSpoofingActive,
                hasPreview = preview != null,
                isPreviewLoading = isPreviewLoading,
                showViewOnMap = false,
                routePlaying = routePlaying,
                onDraftChange = {
                    if (it.kind != draft.kind) preview = null
                    onDraftChange(it)
                },
                onGenerate = { kind ->
                    val next = draft.copy(kind = kind)
                    onDraftChange(next)
                    scope.launch {
                        isPreviewLoading = true
                        try {
                            preview = onGenerate(next)?.takeIf { it.size >= 2 }
                        } finally {
                            isPreviewLoading = false
                        }
                    }
                },
                onStart = { kind ->
                    onStart(draft.copy(kind = kind), preview)
                    onDismiss()
                },
                onViewOnMap = onDismiss,
            )
        }
    }
}
