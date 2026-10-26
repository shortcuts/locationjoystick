package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.component.LjSelectionMode
import com.locationjoystick.core.designsystem.component.LjSelectionRow
import com.locationjoystick.core.model.GeocodingProviderId
import com.locationjoystick.feature.settings.impl.R

@Composable
internal fun GeocodingSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_menus_geocoding_section))
    Spacer(Modifier.height(LjSpacing.xs))
    Text(
        stringResource(R.string.settings_menus_geocoding_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(LjSpacing.sm))
    val enabledCount = GeocodingProviderId.entries.count { it !in uiState.disabledGeocodingProviders }
    GeocodingProviderId.entries.forEach { id ->
        val checked = id !in uiState.disabledGeocodingProviders
        LjSelectionRow(
            mode = LjSelectionMode.Multi,
            checked = checked,
            title =
                stringResource(
                    when (id) {
                        GeocodingProviderId.NOMINATIM -> R.string.settings_menus_geocoding_nominatim
                        GeocodingProviderId.PHOTON -> R.string.settings_menus_geocoding_photon
                    },
                ),
            enabled = !(checked && enabledCount == 1),
            onCheckedChange = { isChecked ->
                val disabled = uiState.disabledGeocodingProviders
                onAction(SettingsAction.SetDisabledGeocodingProviders(if (isChecked) disabled - id else disabled + id))
            },
        )
    }
}
