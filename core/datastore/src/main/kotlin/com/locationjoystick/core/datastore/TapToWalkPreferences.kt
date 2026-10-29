package com.locationjoystick.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.StoredGroundCalibration
import kotlinx.coroutines.flow.Flow

interface TapToWalkPreferences {
    /** Gets whether the screen tap-to-walk overlay is enabled. */
    fun getTapToWalkOverlayEnabled(): Flow<Boolean>

    suspend fun setTapToWalkOverlayEnabled(enabled: Boolean)

    fun getAccessibilityDisclosureChoice(): Flow<String>

    /**
     * The latest tap-to-walk automatic fit result, or null. Per-device: a standalone key outside
     * [SettingsSnapshot] on purpose, so it is never exported or imported.
     */
    fun getGroundCalibration(): Flow<StoredGroundCalibration?>

    /** Stores [value]; null removes it. */
    suspend fun setGroundCalibration(value: StoredGroundCalibration?)

    suspend fun setAccessibilityDisclosureChoice(choice: String)
}

internal class TapToWalkStore(
    private val dataStore: DataStore<Preferences>,
) : TapToWalkPreferences {
    override fun getTapToWalkOverlayEnabled(): Flow<Boolean> = dataStore.pref(Keys.TAP_TO_WALK_OVERLAY_ENABLED, false)

    override suspend fun setTapToWalkOverlayEnabled(enabled: Boolean) = dataStore.setPref(Keys.TAP_TO_WALK_OVERLAY_ENABLED, enabled)

    override fun getAccessibilityDisclosureChoice(): Flow<String> =
        dataStore.pref(Keys.ACCESSIBILITY_DISCLOSURE_CHOICE, AppConstants.AccessibilityConstants.DISCLOSURE_UNANSWERED)

    override suspend fun setAccessibilityDisclosureChoice(choice: String) = dataStore.setPref(Keys.ACCESSIBILITY_DISCLOSURE_CHOICE, choice)

    override fun getGroundCalibration(): Flow<StoredGroundCalibration?> =
        dataStore.pref(Keys.TAP_TO_WALK_GROUND_CALIBRATION, "") { if (it.isEmpty()) null else deserializeGroundCalibration(it) }

    override suspend fun setGroundCalibration(value: StoredGroundCalibration?) {
        dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(Keys.TAP_TO_WALK_GROUND_CALIBRATION)
            } else {
                prefs[Keys.TAP_TO_WALK_GROUND_CALIBRATION] = serializeGroundCalibration(value)
            }
        }
    }
}
