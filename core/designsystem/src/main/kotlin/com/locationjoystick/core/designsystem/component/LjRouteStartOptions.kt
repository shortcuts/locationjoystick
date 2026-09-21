package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.R

@Composable
fun LjRouteStartCheckboxes(
    loop: Boolean,
    onLoopChange: (Boolean) -> Unit,
    reverse: Boolean,
    onReverseChange: (Boolean) -> Unit,
    returnToLocation: Boolean,
    onReturnToLocationChange: (Boolean) -> Unit,
    followRoads: Boolean,
    onFollowRoadsChange: (Boolean) -> Unit,
    planting: Boolean,
    onPlantingChange: (Boolean) -> Unit,
    teleportBetweenWaypoints: Boolean = false,
    onTeleportBetweenWaypointsChange: (Boolean) -> Unit = {},
    teleportBetweenDelaySecondsText: String =
        AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS.toString(),
    onTeleportBetweenDelaySecondsTextChange: (String) -> Unit = {},
    hideTeleport: Boolean = false,
    enabled: Boolean = true,
    isTeleportRoute: Boolean = false,
    textColor: Color = Color.Unspecified,
) {
    // Drop the 48.dp checkbox min size so Loop/Planting/Reverse/Return/Follow roads/
    // Teleport between waypoints fit a phone sheet without scrolling past Start.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LjCheckboxRow(
                title = stringResource(R.string.route_start_options_loop),
                checked = loop || planting,
                enabled = enabled && !returnToLocation && !planting,
                onCheckedChange = onLoopChange,
                textColor = textColor,
            )
            LjCheckboxRow(
                title = stringResource(R.string.lj_route_start_options_planting),
                checked = planting,
                enabled = enabled && !isTeleportRoute,
                onCheckedChange = onPlantingChange,
                textColor = textColor,
            )
            LjCheckboxRow(
                title = stringResource(R.string.route_start_options_reverse),
                checked = reverse,
                enabled = enabled,
                onCheckedChange = onReverseChange,
                textColor = textColor,
            )
            LjCheckboxRow(
                title = stringResource(R.string.lj_route_start_options_return_to_location),
                checked = returnToLocation && !planting,
                enabled = enabled && !loop && !planting,
                onCheckedChange = onReturnToLocationChange,
                textColor = textColor,
            )
            LjCheckboxRow(
                title = stringResource(R.string.lj_route_start_options_follow_roads),
                checked = followRoads,
                enabled = enabled && !isTeleportRoute,
                onCheckedChange = onFollowRoadsChange,
                textColor = textColor,
            )
            if (!hideTeleport && !isTeleportRoute) {
                LjCheckboxRow(
                    title = stringResource(R.string.lj_route_start_options_teleport_between_waypoints),
                    checked = teleportBetweenWaypoints,
                    enabled = enabled,
                    onCheckedChange = onTeleportBetweenWaypointsChange,
                    textColor = textColor,
                    trailing = {
                        OutlinedTextField(
                            value = teleportBetweenDelaySecondsText,
                            onValueChange = { text ->
                                if (text.isEmpty() || text.all { it.isDigit() }) {
                                    onTeleportBetweenDelaySecondsTextChange(text.take(3))
                                }
                            },
                            enabled = enabled,
                            singleLine = true,
                            label = {
                                Text(
                                    stringResource(R.string.lj_route_start_options_delay_s),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier =
                                Modifier
                                    .padding(end = 8.dp)
                                    .width(DELAY_FIELD_WIDTH)
                                    .defaultMinSize(minWidth = DELAY_FIELD_WIDTH, minHeight = DELAY_FIELD_HEIGHT)
                                    .height(DELAY_FIELD_HEIGHT),
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
        }
    }
}

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
    planting: Boolean,
    onPlantingChange: (Boolean) -> Unit,
    teleportBetweenWaypoints: Boolean = false,
    onTeleportBetweenWaypointsChange: (Boolean) -> Unit = {},
    teleportBetweenDelaySecondsText: String =
        AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS.toString(),
    onTeleportBetweenDelaySecondsTextChange: (String) -> Unit = {},
    onTeleport: () -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    hideTeleport: Boolean = false,
    isTeleportRoute: Boolean = false,
    textColor: Color = Color.Unspecified,
    isStarting: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        LjRouteStartCheckboxes(
            loop = loop,
            onLoopChange = onLoopChange,
            reverse = reverse,
            onReverseChange = onReverseChange,
            returnToLocation = returnToLocation,
            onReturnToLocationChange = onReturnToLocationChange,
            followRoads = followRoads,
            onFollowRoadsChange = onFollowRoadsChange,
            isTeleportRoute = isTeleportRoute,
            planting = planting,
            onPlantingChange = onPlantingChange,
            teleportBetweenWaypoints = teleportBetweenWaypoints,
            onTeleportBetweenWaypointsChange = onTeleportBetweenWaypointsChange,
            teleportBetweenDelaySecondsText = teleportBetweenDelaySecondsText,
            onTeleportBetweenDelaySecondsTextChange = onTeleportBetweenDelaySecondsTextChange,
            hideTeleport = hideTeleport,
            textColor = textColor,
        )
        if (!hideTeleport) {
            Spacer(Modifier.height(LjSpacing.sm))
            OutlinedButton(onClick = onTeleport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.route_start_options_teleport), color = textColor)
            }
        }
        Spacer(Modifier.height(LjSpacing.sm))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LjSpacing.sm)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.route_start_options_cancel), color = textColor)
            }
            Button(onClick = onStart, enabled = !isStarting, modifier = Modifier.weight(1f)) {
                if (isStarting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.common_start), color = textColor)
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun LjRouteStartOptionsPreview() {
    LjTheme {
        LjRouteStartOptions(
            loop = true,
            onLoopChange = {},
            reverse = false,
            onReverseChange = {},
            returnToLocation = false,
            onReturnToLocationChange = {},
            followRoads = false,
            onFollowRoadsChange = {},
            planting = false,
            onPlantingChange = {},
            onTeleport = {},
            onCancel = {},
            onStart = {},
        )
    }
}

@Preview(showBackground = true, name = "Light")
@Composable
private fun LjRouteStartOptionsLightPreview() {
    LjTheme(darkTheme = false) {
        LjRouteStartOptions(
            loop = true,
            onLoopChange = {},
            reverse = false,
            onReverseChange = {},
            returnToLocation = false,
            onReturnToLocationChange = {},
            followRoads = false,
            onFollowRoadsChange = {},
            planting = false,
            onPlantingChange = {},
            onTeleport = {},
            onCancel = {},
            onStart = {},
        )
    }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun LjRouteStartCheckboxesPreview() {
    LjTheme {
        LjRouteStartCheckboxes(
            loop = true,
            onLoopChange = {},
            reverse = false,
            onReverseChange = {},
            returnToLocation = false,
            onReturnToLocationChange = {},
            followRoads = false,
            onFollowRoadsChange = {},
            planting = false,
            onPlantingChange = {},
        )
    }
}

private val DELAY_FIELD_WIDTH = 120.dp
private val DELAY_FIELD_HEIGHT = 48.dp
