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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 */
@Composable
fun CompassDisclosureDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    title: String = stringResource(R.string.compass_disclosure_title),
    acceptLabel: String = stringResource(R.string.compass_disclosure_accept),
    caveats: List<String> = emptyList(),
    showAccessibilityDisclosure: Boolean = true,
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
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    LjIcons.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(title, style = MaterialTheme.typography.headlineSmall)
                caveats.forEach { Text(it) }
                if (showAccessibilityDisclosure) {
                    Text(stringResource(R.string.compass_disclosure_intro))
                    Text(stringResource(R.string.compass_disclosure_data))
                    Text(stringResource(R.string.compass_disclosure_purpose))
                    Text(stringResource(R.string.compass_disclosure_handling))
                    Text(stringResource(R.string.compass_disclosure_optional))
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
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CompassDisclosureDialogPreview() {
    LjTheme { CompassDisclosureDialog(onAccept = {}, onDecline = {}) }
}
