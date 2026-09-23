package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.R

/**
 * Play's Accessibility API policy requires a full-screen, in-flow disclosure that names the
 * AccessibilityService API, the data it reads and why, with an explicit accept tap, before the
 * user reaches Android's accessibility consent screen. Back and outside taps must not consent,
 * so the dialog is not dismissible — [onDecline] is the only way out besides [onAccept].
 */
@Composable
fun CompassDisclosureDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
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
                Text(
                    stringResource(R.string.compass_disclosure_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(R.string.compass_disclosure_intro))
                Text(stringResource(R.string.compass_disclosure_data))
                Text(stringResource(R.string.compass_disclosure_purpose))
                Text(stringResource(R.string.compass_disclosure_handling))
                Text(stringResource(R.string.compass_disclosure_optional))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LjOutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.compass_disclosure_decline))
                    }
                    LjButton(onClick = onAccept, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.compass_disclosure_accept))
                    }
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
