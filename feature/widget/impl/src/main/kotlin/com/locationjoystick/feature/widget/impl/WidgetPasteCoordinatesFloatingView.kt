package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.component.PasteCoordinatesForm
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RouteStartConfig
import com.locationjoystick.feature.widget.impl.R

@Composable
internal fun PasteCoordinatesFloatingView(
    onDismiss: () -> Unit,
    onTeleport: (LatLng) -> Unit,
    onWalk: (LatLng) -> Unit,
    onWalkViaRoads: (LatLng) -> Unit,
    onSaveFavorite: (name: String, position: LatLng) -> Unit,
    onSaveRoute: (name: String, points: List<LatLng>) -> Unit,
    onStartRoute: (points: List<LatLng>, config: RouteStartConfig) -> Unit,
    hideTeleport: Boolean = false,
    minimized: Boolean = false,
    onToggleMinimize: () -> Unit = {},
) {
    FloatingPickerShell(
        title = stringResource(R.string.widget_panel_content_paste_coordinates),
        onDismiss = onDismiss,
        hasBack = false,
        onBack = onDismiss,
        compactOverlay = true,
        minimized = minimized,
        onToggleMinimize = onToggleMinimize,
    ) {
        PasteCoordinatesForm(
            onDismiss = onDismiss,
            onTeleport = onTeleport,
            onWalk = onWalk,
            onWalkViaRoads = onWalkViaRoads,
            onSaveFavorite = onSaveFavorite,
            onSaveRoute = onSaveRoute,
            onStartRoute = onStartRoute,
            hideTeleportFeatures = hideTeleport,
            showTitle = false,
            contentPadding = PaddingValues(0.dp),
        )
    }
}
