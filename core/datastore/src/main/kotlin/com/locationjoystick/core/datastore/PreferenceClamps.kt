package com.locationjoystick.core.datastore

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.MAX_JITTER_RADIUS_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.MAX_JITTER_STEP_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.MIN_JITTER_STEP_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.MIN_SPEED_MS

// The setters and applySnapshot must clamp identically, so both call these.
internal fun Double.coerceSpeedMs(): Double = coerceAtLeast(MIN_SPEED_MS)

internal fun Double.coerceJitterRadius(): Double = coerceIn(0.0, MAX_JITTER_RADIUS_METERS)

internal fun Double.coerceJitterStep(): Double = coerceIn(MIN_JITTER_STEP_METERS, MAX_JITTER_STEP_METERS)

internal fun Int.coerceSpeedVariationPct(): Int =
    coerceIn(AppConstants.JitterConstants.SPEED_VARIATION_PCT_MIN, AppConstants.JitterConstants.SPEED_VARIATION_PCT_MAX)
