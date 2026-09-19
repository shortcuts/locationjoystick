package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.toLocaleDoubleOrNull
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSegmentedControl
import com.locationjoystick.core.designsystem.component.speedProfileLabel
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.feature.settings.impl.R

@Composable
internal fun SettingsRoamingSubScreen(
    uiState: SettingsUiState,
    roamingDefaults: RoamingDefaults,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = stringResource(R.string.settings_roaming_roaming),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = { SettingsSaveDiscardFab(uiState.isDirty, onAction) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    val isMph = uiState.speedUnit == SpeedUnit.MPH
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(remember { ScrollState(0) })
                                .padding(16.dp),
                    ) {
                        RoamingSection(roamingDefaults, isMph, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoamingSection(
    roamingDefaults: RoamingDefaults,
    isMph: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    Text(stringResource(R.string.settings_roaming_roaming), style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_roaming_default_settings_used_when_starting_a),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(16.dp))

    Text(stringResource(R.string.settings_roaming_sub_screen_walk_around_the_block), style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_roaming_sub_screen_picks_random_spots_within_a_radius_and_walks_between_them),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    var radiusText by remember(isMph) {
        mutableStateOf(
            if (isMph) {
                String.format("%.2f", roamingDefaults.radiusMeters / 1609.344)
            } else {
                roamingDefaults.radiusMeters.toInt().toString()
            },
        )
    }
    OutlinedTextField(
        value = radiusText,
        onValueChange = { text ->
            radiusText = text
            text.toLocaleDoubleOrNull()?.let { v ->
                val meters = if (isMph) v * 1609.344 else v
                onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(radiusMeters = meters.coerceIn(1_000.0, 100_000.0))))
            }
        },
        label = {
            Text(
                if (isMph) {
                    stringResource(R.string.settings_roaming_radius_mi)
                } else {
                    stringResource(R.string.settings_roaming_radius_m)
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = if (isMph) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(4.dp))

    var distanceText by remember(isMph) {
        mutableStateOf(
            if (isMph) {
                String.format("%.2f", roamingDefaults.distanceMeters / 1609.344)
            } else {
                roamingDefaults.distanceMeters.toInt().toString()
            },
        )
    }
    OutlinedTextField(
        value = distanceText,
        onValueChange = { text ->
            distanceText = text
            text.toLocaleDoubleOrNull()?.let { v ->
                val meters = if (isMph) v * 1609.344 else v
                onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(distanceMeters = meters.coerceIn(50.0, 50_000.0))))
            }
        },
        label = {
            Text(
                if (isMph) {
                    stringResource(R.string.settings_roaming_route_distance_mi)
                } else {
                    stringResource(R.string.settings_roaming_route_distance_m)
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = if (isMph) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(stringResource(R.string.settings_roaming_speed_profile), style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    LjSegmentedControl(
        options = SpeedProfile.defaultProfiles().map { it.id to speedProfileLabel(it.id) },
        selected = roamingDefaults.speedProfileId,
        onSelect = { onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(speedProfileId = it))) },
        modifier = Modifier.fillMaxWidth(),
    )

    LjCheckboxRow(
        checked = roamingDefaults.followRoads,
        onCheckedChange = { onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(followRoads = it))) },
        title = stringResource(R.string.settings_roaming_follow_roads),
        description = stringResource(R.string.settings_roaming_follow_roads_desc),
    )
    LjCheckboxRow(
        checked = roamingDefaults.returnToInitialLocation,
        onCheckedChange = { onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(returnToInitialLocation = it))) },
        title = stringResource(R.string.settings_roaming_return_to_start),
        description = stringResource(R.string.settings_roaming_return_to_start_desc),
    )

    Spacer(modifier = Modifier.height(24.dp))
    Text(stringResource(R.string.settings_roaming_sub_screen_planting), style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_spiral_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    var startRadiusText by remember {
        mutableStateOf(roamingDefaults.plantingStartRadiusMeters.toInt().toString())
    }
    OutlinedTextField(
        value = startRadiusText,
        onValueChange = { text ->
            startRadiusText = text
            text.toLocaleDoubleOrNull()?.let { v ->
                onAction(
                    SettingsAction.UpdateRoamingDefaults(
                        roamingDefaults.copy(
                            plantingStartRadiusMeters =
                                v.coerceIn(
                                    AppConstants.RoamingConstants.ROAMING_MIN_RADIUS_METERS,
                                    AppConstants.RoamingConstants.PLANTING_MAX_RADIUS_METERS,
                                ),
                        ),
                    ),
                )
            }
        },
        label = { Text(stringResource(R.string.settings_roaming_sub_screen_starting_radius_m)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(4.dp))

    var endRadiusText by remember {
        mutableStateOf(roamingDefaults.plantingEndRadiusMeters.toInt().toString())
    }
    OutlinedTextField(
        value = endRadiusText,
        onValueChange = { text ->
            endRadiusText = text
            text.toLocaleDoubleOrNull()?.let { v ->
                onAction(
                    SettingsAction.UpdateRoamingDefaults(
                        roamingDefaults.copy(
                            plantingEndRadiusMeters =
                                v.coerceIn(
                                    AppConstants.RoamingConstants.ROAMING_MIN_RADIUS_METERS,
                                    AppConstants.RoamingConstants.PLANTING_MAX_RADIUS_METERS,
                                ),
                        ),
                    ),
                )
            }
        },
        label = { Text(stringResource(R.string.settings_roaming_sub_screen_ending_radius_m)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(8.dp))
    Text(stringResource(R.string.settings_roaming_sub_screen_speed_profile), style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    LjSegmentedControl(
        options =
            listOf(
                AppConstants.ProfileConstants.PROFILE_ID_WALK,
                AppConstants.ProfileConstants.PROFILE_ID_RUN,
                AppConstants.ProfileConstants.PROFILE_ID_BIKE,
            ).map { it to speedProfileLabel(it) },
        selected = roamingDefaults.plantingSpeedProfileId,
        onSelect = { onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(plantingSpeedProfileId = it))) },
        modifier = Modifier.fillMaxWidth(),
    )

    LjCheckboxRow(
        checked = roamingDefaults.plantingInfiniteLoops,
        onCheckedChange = { onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(plantingInfiniteLoops = it))) },
        title = stringResource(R.string.settings_roaming_sub_screen_infinite_loop),
        description = stringResource(R.string.settings_roaming_sub_screen_keeps_expanding_and_contracting_until_you_stop_roaming),
    )

    if (!roamingDefaults.plantingInfiniteLoops) {
        var loopCountText by remember {
            mutableStateOf(roamingDefaults.plantingLoopCount.toString())
        }
        OutlinedTextField(
            value = loopCountText,
            onValueChange = { text ->
                loopCountText = text
                text.toIntOrNull()?.let { count ->
                    onAction(
                        SettingsAction.UpdateRoamingDefaults(
                            roamingDefaults.copy(
                                plantingLoopCount =
                                    count.coerceIn(1, AppConstants.RoamingConstants.PLANTING_MAX_LOOP_COUNT),
                            ),
                        ),
                    )
                }
            },
            label = { Text(stringResource(R.string.settings_roaming_sub_screen_number_of_loops)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
