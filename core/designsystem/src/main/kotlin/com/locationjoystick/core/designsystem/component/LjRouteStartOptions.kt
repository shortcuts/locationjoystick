package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.Column
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
    onWalkAndStart: () -> Unit,
    onWalkViaRoadsAndStart: () -> Unit,
    onTeleportAndStart: () -> Unit,
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
        Spacer(Modifier.height(20.dp))
        Button(onClick = onWalkAndStart, modifier = Modifier.fillMaxWidth()) {
            Text("Walk and start", color = textColor)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onWalkViaRoadsAndStart, modifier = Modifier.fillMaxWidth()) {
            Text("Walk via roads and start", color = textColor)
        }
        if (!hideTeleport) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onTeleportAndStart, modifier = Modifier.fillMaxWidth()) {
                Text("Teleport and start", color = textColor)
            }
        }
    }
}
