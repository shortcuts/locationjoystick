package com.locationjoystick.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.StoredGroundCalibration
import com.locationjoystick.core.model.TapToWalkMove
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TapToWalkPreferences {
    /** Gets whether the screen tap-to-walk overlay is enabled. */
    fun getTapToWalkOverlayEnabled(): Flow<Boolean>

    suspend fun setTapToWalkOverlayEnabled(enabled: Boolean)

    /** The moves a tap offers. Never empty: absent, empty or unknown values read as [TapToWalkMove.DEFAULT]. */
    fun getTapToWalkMoves(): Flow<Set<TapToWalkMove>>

    /** Stores [moves]; an empty set stores the default. */
    suspend fun setTapToWalkMoves(moves: Set<TapToWalkMove>)

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

internal fun Preferences.toTapToWalkMoves(): Set<TapToWalkMove> =
    TapToWalkMove.entries
        .filter { it.name in this[Keys.TAP_TO_WALK_MOVES].orEmpty() }
        .toSet()
        .ifEmpty { TapToWalkMove.DEFAULT }

internal class TapToWalkStore(
    private val dataStore: DataStore<Preferences>,
) : TapToWalkPreferences {
    override fun getTapToWalkOverlayEnabled(): Flow<Boolean> = dataStore.pref(Keys.TAP_TO_WALK_OVERLAY_ENABLED, false)

    override suspend fun setTapToWalkOverlayEnabled(enabled: Boolean) = dataStore.setPref(Keys.TAP_TO_WALK_OVERLAY_ENABLED, enabled)

    override fun getTapToWalkMoves(): Flow<Set<TapToWalkMove>> = dataStore.safeData("tap to walk moves").map { it.toTapToWalkMoves() }

    override suspend fun setTapToWalkMoves(moves: Set<TapToWalkMove>) =
        dataStore.setPref(Keys.TAP_TO_WALK_MOVES, moves.ifEmpty { TapToWalkMove.DEFAULT }.map { it.name }.toSet())

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
