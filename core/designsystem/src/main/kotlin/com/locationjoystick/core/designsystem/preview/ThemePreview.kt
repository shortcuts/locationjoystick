package com.locationjoystick.core.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.designsystem.component.EmptyState
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjCard
import com.locationjoystick.core.designsystem.component.LjMapIconButton
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.core.designsystem.component.LjOutlinedCard

@PreviewLightDark
@Composable
private fun ThemePreview() {
    LjTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.theme_preview_lj),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.theme_preview_design_system_preview),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LjCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.theme_preview_mock_location_active),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = stringResource(R.string.theme_preview_48_8566_n_2_3522_e),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LjButton(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_start))
                    }
                    LjOutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.theme_preview_stop))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LjMapIconButton(
                        icon = LjIcons.MyLocation,
                        contentDescription = "",
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = {},
                    )
                    LjOutlinedCard(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.theme_preview_design_system_preview),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                EmptyState(
                    message = "No routes yet",
                    icon = LjIcons.Route,
                    description = "Tap + to create your first route",
                )
            }
        }
    }
}
