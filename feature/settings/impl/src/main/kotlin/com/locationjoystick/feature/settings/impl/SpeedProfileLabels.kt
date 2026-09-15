package com.locationjoystick.feature.settings.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.locationjoystick.core.model.SpeedProfile

/**
 * Localized name for a speed preset.
 *
 * [SpeedProfile.name] is an English identifier owned by the data layer — it is also written into
 * exported settings files — so the label shown to the user is resolved here from the preset's
 * stable [SpeedProfile.id] instead.
 */
@Composable
internal fun speedProfileLabel(profile: SpeedProfile): String =
    when (profile.id) {
        "slow_walk" -> stringResource(R.string.speed_profile_slow_walk)
        "walk" -> stringResource(R.string.speed_profile_walk)
        "run" -> stringResource(R.string.speed_profile_run)
        "bike" -> stringResource(R.string.speed_profile_bike)
        "drive" -> stringResource(R.string.speed_profile_drive)
        else -> profile.name
    }
