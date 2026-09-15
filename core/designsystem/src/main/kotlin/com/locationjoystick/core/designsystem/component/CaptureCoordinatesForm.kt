package com.locationjoystick.core.designsystem.component

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locationjoystick.core.common.util.formatCapturedPoint
import com.locationjoystick.core.common.util.formatCapturedPointsForClipboard
import com.locationjoystick.core.designsystem.LjAccent
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.LjSuccess
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.LjWarning
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.model.LatLng

@Composable
fun CaptureCoordinatesForm(
    captureModeEnabled: Boolean,
    captureEnabled: Boolean,
    jumpEnabled: Boolean,
    points: List<LatLng>,
    routeName: String,
    saved: Boolean,
    saveError: String?,
    canSave: Boolean,
    passThroughBrowserName: String,
    onCaptureModeEnabledChange: (Boolean) -> Unit,
    onCaptureEnabledChange: (Boolean) -> Unit,
    onJumpEnabledChange: (Boolean) -> Unit,
    onRouteNameChange: (String) -> Unit,
    onSaveRoute: () -> Unit,
    onClearPoints: () -> Unit,
    onRemoveLast: () -> Unit,
    onRequestDefaultBrowser: () -> Unit,
    onOpenThisAppLinks: () -> Unit,
    onOpenMapsLinks: () -> Unit,
    onRestoreDefaultApps: () -> Unit,
    onChoosePassThroughBrowser: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    showClose: Boolean = true,
    isDefaultBrowser: Boolean = false,
    optimizeProximity: Boolean = true,
    onOptimizeProximityChange: (Boolean) -> Unit = {},
    orderedPoints: List<LatLng> = points,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.capture_copied_count, orderedPoints.size)
    val ready = isCaptureReady(captureModeEnabled, captureEnabled, jumpEnabled, isDefaultBrowser)
    var showClearPrompt by remember { mutableStateOf(false) }

    fun requestCaptureMode(enabled: Boolean) {
        if (enabled && !captureModeEnabled && points.isNotEmpty()) {
            showClearPrompt = true
        } else {
            onCaptureModeEnabledChange(enabled)
        }
    }

    if (showClearPrompt) {
        AlertDialog(
            onDismissRequest = { showClearPrompt = false },
            title = { Text(stringResource(R.string.capture_coordinates_form_clear_captured_locations)) },
            text = { Text(stringResource(R.string.capture_previous_points, points.size)) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearPoints()
                        onCaptureModeEnabledChange(true)
                        showClearPrompt = false
                    },
                ) {
                    Text(stringResource(R.string.capture_coordinates_form_clear))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onCaptureModeEnabledChange(true)
                        showClearPrompt = false
                    },
                ) {
                    Text(stringResource(R.string.capture_coordinates_form_keep))
                }
            },
        )
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .imePadding(),
    ) {
        if (showTitle) {
            Text(
                text = stringResource(R.string.capture_coordinates_form_capture_coordinates),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = LjSpacing.xs),
            )
        }
        CaptureSetupSteps(
            captureModeEnabled = captureModeEnabled,
            captureEnabled = captureEnabled,
            jumpEnabled = jumpEnabled,
            isDefaultBrowser = isDefaultBrowser,
            onCaptureModeEnabledChange = ::requestCaptureMode,
            onCaptureEnabledChange = onCaptureEnabledChange,
            onJumpEnabledChange = onJumpEnabledChange,
            onRequestDefaultBrowser = onRequestDefaultBrowser,
            onOpenMapsLinks = onOpenMapsLinks,
            onOpenThisAppLinks = onOpenThisAppLinks,
            onRestoreDefaultApps = onRestoreDefaultApps,
        )
        if (!captureModeEnabled || !captureEnabled && !jumpEnabled) {
            CaptureOffBanner(
                text =
                    if (!captureModeEnabled) {
                        stringResource(R.string.capture_off_message)
                    } else {
                        stringResource(R.string.capture_passthrough_message)
                    },
                modifier = Modifier.padding(top = LjSpacing.sm, bottom = LjSpacing.xs),
            )
        } else if (ready) {
            CaptureReadyBanner(
                captureEnabled = captureEnabled,
                jumpEnabled = jumpEnabled,
                modifier = Modifier.padding(top = LjSpacing.sm, bottom = LjSpacing.xs),
            )
        }
        TextButton(
            onClick = onChoosePassThroughBrowser,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.capture_passthrough_label, passThroughBrowserName))
        }
        Text(
            text =
                if (points.isEmpty()) {
                    stringResource(R.string.capture_empty)
                } else {
                    stringResource(R.string.capture_point_count, points.size)
                },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = LjSpacing.sm),
        )
        OutlinedTextField(
            value =
                points
                    .mapIndexed { index, point -> "${index + 1}. ${formatCapturedPoint(point)}" }
                    .joinToString("\n"),
            onValueChange = {},
            readOnly = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = LjSpacing.xs),
            placeholder = { Text(stringResource(R.string.capture_coordinates_form_no_captured_points_yet)) },
            minLines = 3,
            maxLines = 8,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LjAccent,
                    unfocusedBorderColor = LjAccent,
                    disabledBorderColor = LjAccent,
                ),
        )
        Text(
            stringResource(R.string.capture_coordinates_form_point_order),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = LjSpacing.sm),
        )
        LjSegmentedControl(
            options =
                listOf(
                    false to stringResource(R.string.capture_original_order),
                    true to stringResource(R.string.capture_optimize_order),
                ),
            selected = optimizeProximity,
            onSelect = onOptimizeProximityChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = LjSpacing.xs),
        )
        Text(
            stringResource(R.string.capture_coordinates_form_the_list_stays_in_capture_order_so_you_can_remove_the_last_p),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = LjSpacing.xs),
        )
        CapturePointActions(
            enabled = points.isNotEmpty(),
            onCopy = {
                clipboard.setText(AnnotatedString(formatCapturedPointsForClipboard(orderedPoints)))
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            },
            onRemoveLast = onRemoveLast,
            onClearPoints = onClearPoints,
            modifier = Modifier.padding(top = LjSpacing.xs),
        )
        OutlinedTextField(
            value = routeName,
            onValueChange = onRouteNameChange,
            label = { Text(stringResource(R.string.capture_coordinates_form_route_name)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = LjSpacing.xs),
            singleLine = true,
            supportingText = {
                Text(saveError ?: if (saved) stringResource(R.string.capture_saved) else stringResource(R.string.capture_need_points))
            },
            isError = saveError != null,
        )
        Button(
            onClick = onSaveRoute,
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.capture_coordinates_form_save_as_route))
        }
        if (showClose) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.capture_coordinates_form_close))
            }
        }
    }
}

@Composable
private fun CaptureSetupSteps(
    captureModeEnabled: Boolean,
    captureEnabled: Boolean,
    jumpEnabled: Boolean,
    isDefaultBrowser: Boolean,
    onCaptureModeEnabledChange: (Boolean) -> Unit,
    onCaptureEnabledChange: (Boolean) -> Unit,
    onJumpEnabledChange: (Boolean) -> Unit,
    onRequestDefaultBrowser: () -> Unit,
    onOpenMapsLinks: () -> Unit,
    onOpenThisAppLinks: () -> Unit,
    onRestoreDefaultApps: () -> Unit,
) {
    CaptureToggleStep(
        captureModeEnabled = captureModeEnabled,
        captureEnabled = captureEnabled,
        jumpEnabled = jumpEnabled,
        onCaptureModeEnabledChange = onCaptureModeEnabledChange,
        onCaptureEnabledChange = onCaptureEnabledChange,
        onJumpEnabledChange = onJumpEnabledChange,
    )
    CaptureStepRow(
        marker = captureBrowserMarker(isDefaultBrowser),
        number = "2",
        extraSpacing = true,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaptureLinkedText(
            prefix =
                if (isDefaultBrowser) {
                    stringResource(
                        R.string.capture_browser_prefix_is,
                    )
                } else {
                    stringResource(R.string.capture_browser_prefix_set)
                },
            linkText = stringResource(R.string.capture_coordinates_form_default_browser),
            onClick = onRequestDefaultBrowser,
        )
    }
    CaptureStepRow(marker = CaptureStepMarker.ACTION, number = "3", extraSpacing = true) {
        CaptureLinkedText(
            prefix = stringResource(R.string.capture_coordinates_form_turn_off),
            linkText = stringResource(R.string.capture_coordinates_form_google_maps_supported_links),
            onClick = onOpenMapsLinks,
        )
    }
    CaptureStepRow(marker = CaptureStepMarker.ACTION, number = "4", extraSpacing = true) {
        CaptureLinkedText(
            prefix = stringResource(R.string.capture_coordinates_form_turn_on),
            linkText = stringResource(R.string.capture_coordinates_form_supported_links_for_this_app),
            onClick = onOpenThisAppLinks,
        )
    }
    CaptureStepRow(
        marker = captureRestoreMarker(captureModeEnabled),
        number = "5",
        extraSpacing = true,
        verticalAlignment = Alignment.Top,
    ) {
        CaptureLinkedText(
            prefix = stringResource(R.string.capture_coordinates_form_when_you_are_done),
            linkText = stringResource(R.string.capture_coordinates_form_restore_default_browser),
            suffix = stringResource(R.string.capture_coordinates_form_and_reverse_steps_3_4),
            onClick = onRestoreDefaultApps,
        )
    }
}

@Composable
private fun CaptureToggleStep(
    captureModeEnabled: Boolean,
    captureEnabled: Boolean,
    jumpEnabled: Boolean,
    onCaptureModeEnabledChange: (Boolean) -> Unit,
    onCaptureEnabledChange: (Boolean) -> Unit,
    onJumpEnabledChange: (Boolean) -> Unit,
) {
    CaptureStepRow(
        marker = captureToggleMarker(captureModeEnabled),
        number = "1",
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onCaptureModeEnabledChange(!captureModeEnabled) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.capture_coordinates_form_capture_mode),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = captureModeEnabled, onCheckedChange = onCaptureModeEnabledChange)
            }
            CaptureFunctionToggle(
                label = stringResource(R.string.capture_coordinates_form_list),
                description = stringResource(R.string.capture_coordinates_form_add_each_location_to_the_list),
                checked = captureEnabled,
                enabled = captureModeEnabled,
                onCheckedChange = onCaptureEnabledChange,
            )
            CaptureFunctionToggle(
                label = stringResource(R.string.capture_coordinates_form_jump),
                description = stringResource(R.string.capture_coordinates_form_teleport_to_each_location_immediately),
                checked = jumpEnabled,
                enabled = captureModeEnabled,
                onCheckedChange = onJumpEnabledChange,
            )
        }
    }
}

@Composable
private fun CaptureFunctionToggle(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CaptureStepRow(
    marker: CaptureStepMarker,
    number: String,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    extraSpacing: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = if (extraSpacing) LjSpacing.sm else 2.dp),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(LjSpacing.sm),
    ) {
        Box(modifier = Modifier.padding(top = if (verticalAlignment == Alignment.Top) 2.dp else 0.dp)) {
            CaptureStepMarkerBadge(marker = marker, number = number)
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
        trailing?.invoke()
    }
}

@Composable
private fun CaptureStepMarkerBadge(
    marker: CaptureStepMarker,
    number: String,
) {
    val borderColor: Color
    val contentColor: Color
    val background: Color
    when (marker) {
        CaptureStepMarker.NEEDED -> {
            borderColor = LjWarning
            contentColor = LjWarning
            background = LjWarning.copy(alpha = 0.2f)
        }
        CaptureStepMarker.DONE, CaptureStepMarker.RESTORE -> {
            borderColor = LjSuccess
            contentColor = LjSuccess
            background = LjSuccess.copy(alpha = 0.2f)
        }
        CaptureStepMarker.ACTION -> {
            borderColor = LjAccent
            contentColor = LjAccent
            background = Color.Transparent
        }
        CaptureStepMarker.IDLE -> {
            borderColor = MaterialTheme.colorScheme.outlineVariant
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            background = Color.Transparent
        }
    }
    Box(
        modifier =
            Modifier
                .size(20.dp)
                .border(1.5.dp, borderColor, CircleShape)
                .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (marker == CaptureStepMarker.DONE) {
            Icon(
                imageVector = LjIcons.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp),
            )
        } else {
            Text(
                text = number,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CaptureLinkedText(
    prefix: String,
    linkText: String,
    onClick: () -> Unit,
    suffix: String = "",
) {
    val accent = MaterialTheme.colorScheme.primary
    val annotated =
        buildAnnotatedString {
            append(prefix)
            withLink(
                LinkAnnotation.Clickable(
                    tag = linkText,
                    styles =
                        TextLinkStyles(
                            style =
                                SpanStyle(
                                    color = accent,
                                    textDecoration = TextDecoration.Underline,
                                ),
                        ),
                    linkInteractionListener = { onClick() },
                ),
            ) {
                append(linkText)
            }
            append(suffix)
        }
    Text(text = annotated, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun CaptureReadyBanner(
    captureEnabled: Boolean,
    jumpEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = LjSuccess.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, LjSuccess.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(LjSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = LjSuccess,
            ) {
                Text(
                    text = stringResource(R.string.capture_coordinates_form_ready),
                    color = Color(0xFF113311),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                text =
                    when {
                        captureEnabled && jumpEnabled -> stringResource(R.string.capture_ready_both)
                        captureEnabled -> stringResource(R.string.capture_ready_list)
                        else -> stringResource(R.string.capture_ready_jump)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CaptureOffBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = LjWarning.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, LjWarning.copy(alpha = 0.4f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CapturePointActions(
    enabled: Boolean,
    onCopy: () -> Unit,
    onRemoveLast: () -> Unit,
    onClearPoints: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val copyDescription = stringResource(R.string.capture_coordinates_form_copy_coordinates)
    val removeDescription = stringResource(R.string.capture_coordinates_form_remove_last)
    val clearDescription = stringResource(R.string.capture_coordinates_form_clear_list)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onCopy,
            enabled = enabled,
            contentPadding = buttonPadding,
            modifier = Modifier.semantics { contentDescription = copyDescription },
        ) {
            Icon(
                imageVector = LjIcons.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.capture_coordinates_form_copy))
        }
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onRemoveLast,
            enabled = enabled,
            contentPadding = buttonPadding,
            modifier = Modifier.semantics { contentDescription = removeDescription },
        ) {
            Icon(
                imageVector = LjIcons.Undo,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.capture_coordinates_form_last))
        }
        Spacer(modifier = Modifier.width(LjSpacing.sm))
        OutlinedButton(
            onClick = onClearPoints,
            enabled = enabled,
            contentPadding = buttonPadding,
            modifier = Modifier.semantics { contentDescription = clearDescription },
        ) {
            Icon(
                imageVector = LjIcons.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.capture_coordinates_form_clear))
        }
    }
}

@Preview
@Composable
private fun CaptureCoordinatesFormPreview() {
    LjTheme {
        CaptureCoordinatesForm(
            captureModeEnabled = true,
            captureEnabled = true,
            jumpEnabled = false,
            points = listOf(LatLng(36.977695, 128.363905), LatLng(36.982194, 128.370129)),
            routeName = "Morning route",
            saved = false,
            saveError = null,
            canSave = true,
            passThroughBrowserName = "Chrome",
            onCaptureModeEnabledChange = {},
            onCaptureEnabledChange = {},
            onJumpEnabledChange = {},
            onRouteNameChange = {},
            onSaveRoute = {},
            onClearPoints = {},
            onRemoveLast = {},
            onRequestDefaultBrowser = {},
            onOpenThisAppLinks = {},
            onOpenMapsLinks = {},
            onRestoreDefaultApps = {},
            onChoosePassThroughBrowser = {},
            onDismiss = {},
            isDefaultBrowser = true,
        )
    }
}

@Preview
@Composable
private fun CaptureCoordinatesFormSetupPreview() {
    LjTheme {
        CaptureCoordinatesForm(
            captureModeEnabled = false,
            captureEnabled = false,
            jumpEnabled = false,
            points = emptyList(),
            routeName = "",
            saved = false,
            saveError = null,
            canSave = false,
            passThroughBrowserName = "Chrome",
            onCaptureModeEnabledChange = {},
            onCaptureEnabledChange = {},
            onJumpEnabledChange = {},
            onRouteNameChange = {},
            onSaveRoute = {},
            onClearPoints = {},
            onRemoveLast = {},
            onRequestDefaultBrowser = {},
            onOpenThisAppLinks = {},
            onOpenMapsLinks = {},
            onRestoreDefaultApps = {},
            onChoosePassThroughBrowser = {},
            onDismiss = {},
            isDefaultBrowser = false,
        )
    }
}
