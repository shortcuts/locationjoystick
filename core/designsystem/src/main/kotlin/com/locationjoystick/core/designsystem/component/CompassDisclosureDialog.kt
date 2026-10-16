package com.locationjoystick.core.designsystem.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.R

/**
 * Play's Accessibility API policy requires a full-screen, in-flow disclosure that names the
 * AccessibilityService API, the data it reads and why, with an explicit accept tap, before the
 * user reaches Android's accessibility consent screen. Back and outside taps must not consent,
 * so the dialog is not dismissible — [onDecline] is the only way out besides [onAccept].
 *
 * [caveats] and [showAccessibilityDisclosure] let the Tap to Walk switch reuse this one screen for
 * its own risk warning, so enabling the feature is a single surface instead of a dialog then a
 * screen. Below API 30 compass tracking cannot run, so only the caveats are shown.
 *
 * [onSkip], when set, adds a small bottom-right Skip button for the first-run prompt: it neither
 * consents nor opens settings, it only marks the prompt as asked.
 *
 * The body scrolls; Decline/Accept (and Skip) stay pinned at the bottom so they are always visible.
 */
@Composable
fun CompassDisclosureDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    title: String = stringResource(R.string.compass_disclosure_title),
    acceptLabel: String = stringResource(R.string.compass_disclosure_accept),
    caveats: List<String> = emptyList(),
    showAccessibilityDisclosure: Boolean = true,
    onSkip: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDecline,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        LjIcons.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    caveats.forEach { DisclosurePanel { Text(it, style = MaterialTheme.typography.bodyLarge) } }
                    if (showAccessibilityDisclosure) {
                        DisclosurePanel {
                            Text(stringResource(R.string.compass_disclosure_intro), style = MaterialTheme.typography.bodyLarge)
                        }
                        DisclosurePanel { DisclosureFact(stringResource(R.string.compass_disclosure_data)) }
                        DisclosurePanel { DisclosureFact(stringResource(R.string.compass_disclosure_purpose)) }
                        DisclosurePanel { DisclosureFact(stringResource(R.string.compass_disclosure_handling)) }
                        DisclosurePanel { DisclosureFact(stringResource(R.string.compass_disclosure_optional)) }
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.AppInfo.TAP_TO_WALK_GUIDE_URL)),
                            )
                        },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(stringResource(R.string.compass_disclosure_guide))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LjOutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.compass_disclosure_decline))
                    }
                    LjButton(onClick = onAccept, modifier = Modifier.weight(1f)) { Text(acceptLabel) }
                }
                if (onSkip != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onSkip) {
                            Text(
                                stringResource(R.string.compass_disclosure_skip),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclosurePanel(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}

// The strings start with a "Label:" lead-in in every locale, so bolding up to the first colon avoids
// splitting each string into label + body (and re-translating them).
@Composable
private fun DisclosureFact(text: String) {
    val colon = text.indexOfFirst { it == ':' || it == '：' }
    Text(
        buildAnnotatedString {
            if (colon > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(0, colon + 1)) }
                append(text.substring(colon + 1))
            } else {
                append(text)
            }
        },
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
    )
}

@Preview(showBackground = true)
@Composable
private fun CompassDisclosureDialogPreview() {
    LjTheme { CompassDisclosureDialog(onAccept = {}, onDecline = {}) }
}

@Preview(showBackground = true)
@Composable
private fun CompassDisclosureDialogWithSkipPreview() {
    LjTheme { CompassDisclosureDialog(onAccept = {}, onDecline = {}, onSkip = {}) }
}
