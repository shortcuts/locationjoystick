package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import com.locationjoystick.core.common.util.CaptureBrowserChoice
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.R

/** Capture-mode setup steps 2-5 plus the passthrough-browser picker — see docs/features/capture-coordinates.md. */
data class CaptureSetupState(
    val captureModeEnabled: Boolean,
    val isDefaultBrowser: Boolean,
    val passThroughBrowserName: String,
    val browserChoices: List<CaptureBrowserChoice>,
    val selectedBrowserPackage: String?,
    val onSelectBrowser: (String) -> Unit,
    val onRequestDefaultBrowser: () -> Unit,
    val onOpenMapsLinks: () -> Unit,
    val onOpenThisAppLinks: () -> Unit,
    val onRestoreDefaultApps: () -> Unit,
)

@Composable
fun CaptureSetupForm(
    state: CaptureSetupState,
    modifier: Modifier = Modifier,
) {
    var showBrowserPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        CaptureStepRow(
            marker = captureBrowserMarker(state.isDefaultBrowser),
            number = "1",
            extraSpacing = true,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CaptureLinkedText(
                prefix =
                    if (state.isDefaultBrowser) {
                        stringResource(R.string.capture_browser_prefix_is)
                    } else {
                        stringResource(R.string.capture_browser_prefix_set)
                    },
                linkText = stringResource(R.string.capture_coordinates_form_default_browser),
                onClick = state.onRequestDefaultBrowser,
            )
        }
        CaptureStepRow(marker = CaptureStepMarker.ACTION, number = "2", extraSpacing = true) {
            CaptureLinkedText(
                prefix = stringResource(R.string.capture_coordinates_form_turn_off),
                linkText = stringResource(R.string.capture_coordinates_form_google_maps_supported_links),
                onClick = state.onOpenMapsLinks,
            )
        }
        CaptureStepRow(marker = CaptureStepMarker.ACTION, number = "3", extraSpacing = true) {
            CaptureLinkedText(
                prefix = stringResource(R.string.capture_coordinates_form_turn_on),
                linkText = stringResource(R.string.capture_coordinates_form_supported_links_for_this_app),
                onClick = state.onOpenThisAppLinks,
            )
        }
        CaptureStepRow(
            marker = captureRestoreMarker(state.captureModeEnabled),
            number = "4",
            extraSpacing = true,
            verticalAlignment = Alignment.Top,
        ) {
            CaptureLinkedText(
                prefix = stringResource(R.string.capture_coordinates_form_when_you_are_done),
                linkText = stringResource(R.string.capture_coordinates_form_restore_default_browser),
                suffix = stringResource(R.string.capture_coordinates_form_and_reverse_steps_3_4),
                onClick = state.onRestoreDefaultApps,
            )
        }
        TextButton(
            onClick = { showBrowserPicker = state.browserChoices.isNotEmpty() },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.capture_passthrough_label, state.passThroughBrowserName))
        }
    }

    if (showBrowserPicker) {
        AlertDialog(
            onDismissRequest = { showBrowserPicker = false },
            title = { Text(stringResource(R.string.capture_setup_form_pass_through_browser)) },
            text = {
                Column {
                    state.browserChoices.forEach { choice ->
                        Row(
                            modifier =
                                Modifier.clickable {
                                    state.onSelectBrowser(choice.packageName)
                                    showBrowserPicker = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = choice.packageName == state.selectedBrowserPackage,
                                onClick = {
                                    state.onSelectBrowser(choice.packageName)
                                    showBrowserPicker = false
                                },
                            )
                            Text(choice.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBrowserPicker = false }) {
                    Text(stringResource(R.string.capture_setup_form_cancel))
                }
            },
        )
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

@Preview
@Composable
private fun CaptureSetupFormPreview() {
    LjTheme {
        CaptureSetupForm(
            state =
                CaptureSetupState(
                    captureModeEnabled = true,
                    isDefaultBrowser = false,
                    passThroughBrowserName = "Chrome",
                    browserChoices = emptyList(),
                    selectedBrowserPackage = null,
                    onSelectBrowser = {},
                    onRequestDefaultBrowser = {},
                    onOpenMapsLinks = {},
                    onOpenThisAppLinks = {},
                    onRestoreDefaultApps = {},
                ),
        )
    }
}
