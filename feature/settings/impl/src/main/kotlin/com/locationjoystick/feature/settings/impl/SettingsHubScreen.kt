package com.locationjoystick.feature.settings.impl

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.DestinationCardSpec
import com.locationjoystick.core.designsystem.component.DestinationHub
import com.locationjoystick.core.designsystem.component.LjOverflowMenu
import com.locationjoystick.core.designsystem.component.LjOverflowMenuSectionLabel
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.feature.settings.api.SettingsExtension
import com.locationjoystick.feature.settings.impl.R

@Composable
internal fun SettingsHubScreen(
    guidedPath: GuidedPath,
    onOpenDrawer: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
    extensions: List<SettingsExtension>,
    onOpenExtension: (String) -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = stringResource(R.string.settings_hub_settings),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = {
            LjOverflowMenu { dismiss ->
                LjOverflowMenuSectionLabel(stringResource(R.string.settings_hub_export), showDivider = false)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_export_via_qr_code)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.QrShare)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_export_settings)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.Export)
                    },
                )
                LjOverflowMenuSectionLabel(stringResource(R.string.settings_hub_import))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_import_from_qr_code)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.QrScan)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_import_via_code)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.QrEnterCode)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_import_from_file)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.Import)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_import_from_gps_joystick)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.ImportGpsJoystick)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_import_from_yamla)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.ImportYamla)
                    },
                )
                LjOverflowMenuSectionLabel(stringResource(R.string.settings_hub_danger))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_reset_all_data), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(LjIcons.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.ResetAllData)
                    },
                )
            }
        },
    ) { paddingValues ->
        DestinationHub(
            paddingValues = paddingValues,
            footer = stringResource(R.string.settings_hub_menu_hint),
            cards =
                listOf(
                    DestinationCardSpec(
                        icon = LjIcons.Settings,
                        title = stringResource(R.string.settings_hub_general),
                        description = stringResource(R.string.settings_hub_general_desc),
                        onClick = { onNavigate(SettingsSection.GENERAL) },
                    ),
                    DestinationCardSpec(
                        icon = LjIcons.Speed,
                        title = stringResource(R.string.settings_hub_movement_and_gps),
                        description = stringResource(R.string.settings_hub_movement_and_gps_desc),
                        onClick = { onNavigate(SettingsSection.GPS) },
                    ),
                    DestinationCardSpec(
                        icon = LjIcons.MyLocation,
                        title = stringResource(R.string.settings_hub_realism),
                        description = stringResource(R.string.settings_hub_realism_desc),
                        onClick = { onNavigate(SettingsSection.REALISM) },
                    ),
                    DestinationCardSpec(
                        icon = LjIcons.Joystick,
                        title = stringResource(R.string.settings_hub_menus),
                        description = stringResource(R.string.settings_hub_menus_desc),
                        onClick = { onNavigate(SettingsSection.MENUS) },
                    ),
                ) +
                    listOfNotNull(
                        DestinationCardSpec(
                            icon = LjIcons.Visibility,
                            title = stringResource(R.string.settings_hub_screen_helpers),
                            description = stringResource(R.string.settings_hub_screen_helpers_desc),
                            onClick = { onNavigate(SettingsSection.SCREEN_HELPERS) },
                        ).takeIf { showsScreenHelpersCard(guidedPath) },
                    ) +
                    listOf(
                        DestinationCardSpec(
                            icon = LjIcons.Favorite,
                            title = stringResource(R.string.settings_hub_favorites_and_routes),
                            description = stringResource(R.string.settings_hub_favorites_and_routes_desc),
                            onClick = { onNavigate(SettingsSection.FAVORITES_ROUTES) },
                        ),
                        DestinationCardSpec(
                            icon = LjIcons.Explore,
                            title = stringResource(R.string.settings_hub_roaming),
                            description = stringResource(R.string.settings_hub_roaming_desc),
                            onClick = { onNavigate(SettingsSection.ROAMING) },
                        ),
                        DestinationCardSpec(
                            icon = LjIcons.Link,
                            title = stringResource(R.string.settings_hub_capture),
                            description = stringResource(R.string.settings_hub_capture_desc),
                            onClick = { onNavigate(SettingsSection.CAPTURE) },
                        ),
                    ) +
                    extensions.map { extension ->
                        DestinationCardSpec(
                            icon = extension.icon,
                            title = extension.title(),
                            description = extension.description(),
                            onClick = { onOpenExtension(extension.id) },
                        )
                    },
        )
    }
}
