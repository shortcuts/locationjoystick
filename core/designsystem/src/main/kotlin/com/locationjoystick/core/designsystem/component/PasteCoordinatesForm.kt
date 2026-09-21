package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.mergeClipboardIntoPasteText
import com.locationjoystick.core.common.util.parsePastedCoordinates
import com.locationjoystick.core.common.util.parseTeleportBetweenDelaySeconds
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RouteStartConfig
import kotlinx.coroutines.launch

private enum class PasteNamePrompt { Favorite, Route }

private const val PASTE_COORDINATES_MAX_LINES = 10

/**
 * Paste-box used by the map FAB, floating map, and widget panel. Parses all valid points from the
 * text once so the three surfaces stay in sync. Exactly one point shows the location actions;
 * multiple points switch to route actions so extra coordinates are never silently discarded.
 * Save favorite remains separate and intentionally saves the first valid point.
 */
@Composable
fun PasteCoordinatesForm(
    onDismiss: () -> Unit,
    onTeleport: (LatLng) -> Unit,
    onWalk: (LatLng) -> Unit,
    onWalkViaRoads: (LatLng) -> Unit,
    onSaveFavorite: (name: String, position: LatLng) -> Unit,
    onSaveRoute: (name: String, points: List<LatLng>) -> Unit,
    onStartRoute: (points: List<LatLng>, config: RouteStartConfig) -> Unit,
    modifier: Modifier = Modifier,
    hideTeleportFeatures: Boolean = false,
    showTitle: Boolean = true,
    title: String? = null,
    initialText: String = "",
    initialRouteName: String = "",
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
) {
    var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText))
    }
    var favoriteName by rememberSaveable { mutableStateOf("") }
    var namePromptKey by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loop by rememberSaveable { mutableStateOf(true) }
    var reverse by rememberSaveable { mutableStateOf(false) }
    var returnToLocation by rememberSaveable { mutableStateOf(false) }
    var followRoads by rememberSaveable { mutableStateOf(false) }
    var planting by rememberSaveable { mutableStateOf(false) }
    var teleportBetweenWaypoints by rememberSaveable { mutableStateOf(false) }
    var teleportBetweenDelaySecondsText by rememberSaveable {
        mutableStateOf(AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS.toString())
    }
    LaunchedEffect(initialText) {
        if (initialText.isNotEmpty() && fieldValue.text != initialText) {
            fieldValue = TextFieldValue(initialText)
        }
    }
    val noCoordinatesMessage = stringResource(R.string.paste_no_coordinates)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val pasteText = fieldValue.text
    val points = remember(pasteText) { parsePastedCoordinates(pasteText) }
    val showScrollFade =
        points.size > PASTE_COORDINATES_MAX_LINES ||
            pasteText.count { it == '\n' } >= PASTE_COORDINATES_MAX_LINES
    val fieldFadeColor = MaterialTheme.colorScheme.surface
    val startPoint = points.firstOrNull()
    val canSaveFavorite = startPoint != null
    val canPlayRoute = points.size >= 2
    val isRouteInput = points.size > 1
    val namePrompt =
        when (namePromptKey) {
            "favorite" -> PasteNamePrompt.Favorite
            "route" -> PasteNamePrompt.Route
            else -> null
        }

    fun runFirstPoint(action: (LatLng) -> Unit) {
        val point = startPoint
        if (point == null) {
            error = noCoordinatesMessage
        } else {
            action(point)
            onDismiss()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
    ) {
        if (showTitle) {
            Text(
                text = title ?: stringResource(R.string.paste_form_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                error = null
            },
            label = { Text(stringResource(R.string.paste_coordinates_form_coordinates)) },
            placeholder = { Text(stringResource(R.string.paste_form_coordinate_example)) },
            supportingText = error?.let { message -> { Text(message) } },
            isError = error != null,
            minLines = 1,
            maxLines = PASTE_COORDINATES_MAX_LINES,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (showTitle) LjSpacing.sm else 0.dp)
                    .verticalEdgeFade(
                        enabled = showScrollFade,
                        fadeColor = fieldFadeColor,
                    ),
            trailingIcon = {
                IconButton(
                    onClick = {
                        scope.launch {
                            val text = clipboard.readPlainText()
                            if (!text.isNullOrBlank()) {
                                fieldValue = TextFieldValue(mergeClipboardIntoPasteText(fieldValue.text, text))
                                error = null
                            }
                        }
                    },
                ) {
                    Icon(LjIcons.ContentPaste, contentDescription = stringResource(R.string.paste_coordinates_form_paste_from_clipboard))
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LjSpacing.sm),
        ) {
            val editPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            TextButton(
                onClick = {
                    fieldValue =
                        fieldValue.copy(
                            selection = TextRange(0, fieldValue.text.length),
                        )
                },
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = editPadding,
            ) {
                Text(stringResource(R.string.paste_coordinates_form_select_all))
            }
            TextButton(
                onClick = {
                    fieldValue = TextFieldValue("")
                    error = null
                },
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = editPadding,
            ) {
                Text(stringResource(R.string.paste_coordinates_form_clear_all))
            }
        }

        if (namePrompt != null) {
            OutlinedTextField(
                value = favoriteName,
                onValueChange = { favoriteName = it },
                label = { Text(stringResource(R.string.paste_coordinates_form_name)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = LjSpacing.sm),
                singleLine = true,
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = LjSpacing.sm),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        namePromptKey = ""
                        favoriteName = ""
                    },
                ) {
                    Text(stringResource(R.string.delete_confirm_dialog_cancel))
                }
                TextButton(
                    onClick = {
                        if (favoriteName.isBlank()) return@TextButton
                        when (namePrompt) {
                            PasteNamePrompt.Favorite -> {
                                val point = startPoint
                                if (point == null) {
                                    error = noCoordinatesMessage
                                    namePromptKey = ""
                                } else {
                                    onSaveFavorite(favoriteName.trim(), point)
                                    onDismiss()
                                }
                            }
                            PasteNamePrompt.Route -> {
                                if (!canPlayRoute) {
                                    error = noCoordinatesMessage
                                    namePromptKey = ""
                                } else {
                                    onSaveRoute(favoriteName.trim(), points)
                                    onDismiss()
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.paste_coordinates_form_save))
                }
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = LjSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(LjSpacing.xs),
            ) {
                val actionPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                if (!isRouteInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LjSpacing.sm),
                    ) {
                        if (!hideTeleportFeatures) {
                            Button(
                                onClick = { runFirstPoint(onTeleport) },
                                modifier = Modifier.weight(1f),
                                contentPadding = actionPadding,
                            ) {
                                Text(
                                    stringResource(R.string.paste_coordinates_form_teleport),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { runFirstPoint(onWalk) },
                            modifier = Modifier.weight(1f),
                            contentPadding = actionPadding,
                        ) {
                            Text(stringResource(R.string.paste_coordinates_form_walk), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { runFirstPoint(onWalkViaRoads) },
                            modifier = Modifier.weight(1f),
                            contentPadding = actionPadding,
                        ) {
                            Text(
                                stringResource(R.string.paste_coordinates_form_walk_via_roads),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.paste_route_count, points.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LjSpacing.sm),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!canSaveFavorite) {
                                error = noCoordinatesMessage
                            } else {
                                namePromptKey = "favorite"
                                favoriteName = ""
                                error = null
                            }
                        },
                        enabled = canSaveFavorite,
                        modifier = Modifier.weight(1f),
                        contentPadding = actionPadding,
                    ) {
                        Text(
                            if (isRouteInput) {
                                stringResource(
                                    R.string.paste_first_favorite,
                                )
                            } else {
                                stringResource(R.string.paste_save_favorite)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isRouteInput) {
                        OutlinedButton(
                            onClick = {
                                namePromptKey = "route"
                                favoriteName = initialRouteName
                                error = null
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = actionPadding,
                        ) {
                            Text(stringResource(R.string.paste_coordinates_form_save_route), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (isRouteInput) {
                    LjRouteStartCheckboxes(
                        loop = loop,
                        onLoopChange = { loop = it },
                        reverse = reverse,
                        onReverseChange = { reverse = it },
                        returnToLocation = returnToLocation,
                        onReturnToLocationChange = { returnToLocation = it },
                        followRoads = followRoads,
                        onFollowRoadsChange = { followRoads = it },
                        planting = planting,
                        onPlantingChange = {
                            planting = it
                            if (it) returnToLocation = false
                        },
                        teleportBetweenWaypoints = teleportBetweenWaypoints && !hideTeleportFeatures,
                        onTeleportBetweenWaypointsChange = { teleportBetweenWaypoints = it },
                        teleportBetweenDelaySecondsText = teleportBetweenDelaySecondsText,
                        onTeleportBetweenDelaySecondsTextChange = { teleportBetweenDelaySecondsText = it },
                        hideTeleport = hideTeleportFeatures,
                    )
                    Button(
                        onClick = {
                            onStartRoute(
                                points,
                                RouteStartConfig(
                                    isLooping = loop || planting,
                                    isReverse = reverse,
                                    isReturnToLocation = returnToLocation && !loop && !planting,
                                    followRoadsToStart = followRoads,
                                    isPlanting = planting,
                                    teleportBetweenWaypoints = teleportBetweenWaypoints && !hideTeleportFeatures,
                                    teleportBetweenDelaySeconds =
                                        parseTeleportBetweenDelaySeconds(teleportBetweenDelaySecondsText),
                                ),
                            )
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.paste_coordinates_form_start_route))
                    }
                }
            }
        }
    }
}

/** Soft top/bottom fade over the inner text so a capped field reads as scrollable. */
private fun Modifier.verticalEdgeFade(
    enabled: Boolean,
    fadeColor: Color,
    fadeHeight: Dp = 28.dp,
    topInset: Dp = 10.dp,
    bottomInset: Dp = 32.dp,
): Modifier {
    if (!enabled) return this
    return drawWithCache {
        val fadePx = fadeHeight.toPx()
        val top = topInset.toPx()
        val bottom = size.height - bottomInset.toPx()
        val topBrush =
            Brush.verticalGradient(
                colors = listOf(fadeColor, Color.Transparent),
                startY = top,
                endY = top + fadePx,
            )
        val bottomBrush =
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, fadeColor),
                startY = bottom - fadePx,
                endY = bottom,
            )
        onDrawWithContent {
            drawContent()
            drawRect(
                brush = topBrush,
                topLeft = Offset(0f, top),
                size = Size(size.width, fadePx),
            )
            drawRect(
                brush = bottomBrush,
                topLeft = Offset(0f, bottom - fadePx),
                size = Size(size.width, fadePx),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PasteCoordinatesFormPreview() {
    LjTheme {
        PasteCoordinatesForm(
            onDismiss = {},
            onTeleport = {},
            onWalk = {},
            onWalkViaRoads = {},
            onSaveFavorite = { _, _ -> },
            onSaveRoute = { _, _ -> },
            onStartRoute = { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PasteCoordinatesFormHideTeleportPreview() {
    LjTheme {
        PasteCoordinatesForm(
            onDismiss = {},
            onTeleport = {},
            onWalk = {},
            onWalkViaRoads = {},
            onSaveFavorite = { _, _ -> },
            onSaveRoute = { _, _ -> },
            onStartRoute = { _, _ -> },
            hideTeleportFeatures = true,
        )
    }
}
