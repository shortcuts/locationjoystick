package com.locationjoystick.feature.map.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.model.SpeedUnit

private const val MPS_TO_KMH = 3.6
private const val MPS_TO_MPH = 2.236936

/** Converts a speed in m/s (null counts as stopped) to [unit]. */
internal fun speedInUnit(
    mps: Float?,
    unit: SpeedUnit,
): Double = (mps ?: 0f) * if (unit == SpeedUnit.KMH) MPS_TO_KMH else MPS_TO_MPH

/** Live speed readout laid over the map while spoofing; same recipe as the hint pill. */
@Composable
internal fun MapSpeedStrip(
    speedMps: Float?,
    unit: SpeedUnit,
    modifier: Modifier = Modifier,
) {
    Text(
        text =
            stringResource(
                if (unit == SpeedUnit.KMH) R.string.map_speed_strip_kmh else R.string.map_speed_strip_mph,
                speedInUnit(speedMps, unit),
            ),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = LjSpacing.xs),
    )
}
