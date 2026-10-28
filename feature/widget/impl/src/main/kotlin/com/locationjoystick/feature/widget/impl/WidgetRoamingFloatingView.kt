package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.locationjoystick.core.designsystem.component.RoamingSheetContent
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.feature.widget.impl.R
import kotlinx.coroutines.launch

@Composable
internal fun RoamingFloatingView(
    draft: RoamingDefaults,
    speedUnit: SpeedUnit,
    hasCurrentPosition: Boolean,
    isSpoofingActive: Boolean,
    routePlaying: Boolean = false,
    onDismiss: () -> Unit,
    onDraftChange: (RoamingDefaults) -> Unit,
    onGenerate: suspend (RoamingDefaults) -> List<LatLng>?,
    onStart: (RoamingDefaults) -> Unit,
) {
    var hasPreview by remember { mutableStateOf(false) }
    var isPreviewLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FloatingPickerShell(
        title = stringResource(R.string.widget_panel_content_roaming),
        onDismiss = onDismiss,
        hasBack = false,
        onBack = onDismiss,
    ) {
        RoamingSheetContent(
            draft = draft,
            speedUnit = speedUnit,
            hasCurrentPosition = hasCurrentPosition,
            isSpoofingActive = isSpoofingActive,
            hasPreview = hasPreview,
            isPreviewLoading = isPreviewLoading,
            showViewOnMap = false,
            routePlaying = routePlaying,
            onDraftChange = onDraftChange,
            onGenerate = { kind ->
                val next = draft.copy(kind = kind)
                onDraftChange(next)
                scope.launch {
                    isPreviewLoading = true
                    try {
                        hasPreview = (onGenerate(next)?.size ?: 0) >= 2
                    } finally {
                        isPreviewLoading = false
                    }
                }
            },
            onStart = { kind ->
                onStart(draft.copy(kind = kind))
                onDismiss()
            },
            onViewOnMap = onDismiss,
        )
    }
}
