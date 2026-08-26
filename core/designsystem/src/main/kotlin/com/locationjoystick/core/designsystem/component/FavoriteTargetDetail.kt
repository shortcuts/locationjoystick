package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.model.FavoriteLocation

/**
 * Shared composable for the "what to do with this favorite" detail — name, coordinates, and
 * teleport/walk/walk-via-roads actions. Used by both the map long-press Favorites sheet and the
 * floating widget's Favorites panel.
 *
 * @param showDismissButton When false, omits the "Do nothing" button — for hosts (e.g. the
 *   widget panel) that already provide their own dismiss affordance.
 * @param textColor Text color override for hosts embedding this on a dark overlay.
 */
@Composable
fun FavoriteTargetDetail(
    favorite: FavoriteLocation,
    onSetLocation: () -> Unit,
    onGoToLocation: () -> Unit,
    onGoToLocationViaRoads: () -> Unit,
    onDismiss: () -> Unit,
    hideTeleportFeatures: Boolean = false,
    showDismissButton: Boolean = true,
    textColor: Color = Color.Unspecified,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Text(favorite.name, style = MaterialTheme.typography.headlineSmall, color = textColor)
        Text(
            formatLatLng(favorite.position.latitude, favorite.position.longitude),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (!hideTeleportFeatures) {
            Button(
                onClick = onSetLocation,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
            ) {
                Text("Set location", color = textColor)
            }
        }
        OutlinedButton(
            onClick = onGoToLocation,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            Text("Walk to location", color = textColor)
        }
        OutlinedButton(
            onClick = onGoToLocationViaRoads,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            Text("Walk via roads", color = textColor)
        }
        if (showDismissButton) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Do nothing", color = textColor)
            }
        }
    }
}
