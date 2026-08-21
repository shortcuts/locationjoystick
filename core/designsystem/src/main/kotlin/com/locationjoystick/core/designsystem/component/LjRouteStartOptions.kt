package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LjRouteStartOptions(
    loop: Boolean,
    onLoopChange: (Boolean) -> Unit,
    reverse: Boolean,
    onReverseChange: (Boolean) -> Unit,
    returnToLocation: Boolean,
    onReturnToLocationChange: (Boolean) -> Unit,
    followRoads: Boolean,
    onFollowRoadsChange: (Boolean) -> Unit,
    onTeleport: () -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    hideTeleport: Boolean = false,
    textColor: Color = Color.Unspecified,
) {
    Column {
        LjCheckboxRow(
            title = "Loop",
            checked = loop,
            enabled = !returnToLocation,
            onCheckedChange = onLoopChange,
            textColor = textColor,
        )
        LjCheckboxRow(
            title = "Reverse",
            checked = reverse,
            onCheckedChange = onReverseChange,
            textColor = textColor,
        )
        LjCheckboxRow(
            title = "Return to location",
            checked = returnToLocation,
            enabled = !loop,
            onCheckedChange = onReturnToLocationChange,
            textColor = textColor,
        )
        LjCheckboxRow(
            title = "Follow roads",
            checked = followRoads,
            onCheckedChange = onFollowRoadsChange,
            textColor = textColor,
        )
        if (!hideTeleport) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onTeleport, modifier = Modifier.fillMaxWidth()) {
                Text("Teleport", color = textColor)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel", color = textColor)
            }
            Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                Text("Start", color = textColor)
            }
        }
    }
}
