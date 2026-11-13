package com.locationjoystick.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.clampPlantingRadius
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ALTITUDE_JITTER_RADIUS_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_BIKE_SPEED_MS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_DRIVE_SPEED_MS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ENABLED_SPEED_PROFILE_IDS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_JITTER_IDLE_RADIUS_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_JITTER_MAX_STEP_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_JITTER_MOVING_RADIUS_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_JITTER_SPEED_IDLE_VARIATION_PCT
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_JITTER_SPEED_IDLE_WOBBLE_PROBABILITY_PCT
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_JITTER_SPEED_MOVING_VARIATION_PCT
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_MAP_FAB_ITEMS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_PLANTING_RADIUS_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_DISTANCE_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_FOLLOW_ROADS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_KIND
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_PLANTING_END_RADIUS_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_PLANTING_EXPAND_ONLY
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_PLANTING_INFINITE_LOOPS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_PLANTING_LOOP_COUNT
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_PLANTING_SPACING_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_PLANTING_SPEED_PROFILE_ID
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_PLANTING_START_RADIUS_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_RADIUS_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_RETURN_TO_START
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_ROAMING_SPEED_PROFILE_ID
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_RUN_SPEED_MS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_SLOW_WALK_SPEED_MS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_WALK_SPEED_MS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.DEFAULT_WIDGET_ITEMS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.MAX_JITTER_RADIUS_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.MAX_JITTER_STEP_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.MIN_JITTER_STEP_METERS
import com.locationjoystick.core.datastore.AppPreferencesDataSource.Companion.MIN_SPEED_MS
import com.locationjoystick.core.model.AppSettings
import com.locationjoystick.core.model.GeocodingProviderId
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.RoamingKind
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsSnapshotPreferences {
    /** Returns all settings needed by the settings UI in a single DataStore scan. */
    fun getSettingsSnapshot(): Flow<SettingsSnapshot>

    /**
     * Bulk import write: writes every field of [snapshot] atomically in one DataStore transaction, except the
     * live fields of [SettingsSnapshot.settings].
     */
    suspend fun applySnapshot(snapshot: SettingsSnapshot)
}

/**
 * [settings] holds every [AppSettings] field. The other fields are the ones [AppSettings] lacks: they
 * travel in `ExportData` beside it.
 */
data class SettingsSnapshot(
    val settings: AppSettings,
    val slowWalkSpeedMs: Double,
    val walkSpeedMs: Double,
    val runSpeedMs: Double,
    val bikeSpeedMs: Double,
    val driveSpeedMs: Double,
    val jitterIdleRadius: Double,
    val jitterMovingRadius: Double,
    val jitterMaxStepMeters: Double,
    val jitterSpeedIdleVariationPct: Int,
    val jitterSpeedMovingVariationPct: Int,
    val jitterSpeedIdleWobbleProbabilityPct: Int = DEFAULT_JITTER_SPEED_IDLE_WOBBLE_PROBABILITY_PCT,
    val hotLocationsEnabled: Boolean,
    val selectedHotLocationIds: Set<String>,
    val hotRoutesEnabled: Boolean,
    val selectedHotRouteIds: Set<String>,
)

internal class SettingsSnapshotStore(
    private val dataStore: DataStore<Preferences>,
) : SettingsSnapshotPreferences {
    override fun getSettingsSnapshot(): Flow<SettingsSnapshot> = dataStore.safeData("settings snapshot").map { it.toSettingsSnapshot() }

    // Live fields have their own immediate setters; a stale snapshot must not overwrite them.
    override suspend fun applySnapshot(snapshot: SettingsSnapshot) {
        val settings = snapshot.settings
        dataStore.edit { prefs ->
            prefs[Keys.SLOW_WALK_SPEED_MS] = snapshot.slowWalkSpeedMs.coerceAtLeast(MIN_SPEED_MS)
            prefs[Keys.WALK_SPEED_MS] = snapshot.walkSpeedMs.coerceAtLeast(MIN_SPEED_MS)
            prefs[Keys.RUN_SPEED_MS] = snapshot.runSpeedMs.coerceAtLeast(MIN_SPEED_MS)
            prefs[Keys.BIKE_SPEED_MS] = snapshot.bikeSpeedMs.coerceAtLeast(MIN_SPEED_MS)
            prefs[Keys.DRIVE_SPEED_MS] = snapshot.driveSpeedMs.coerceAtLeast(MIN_SPEED_MS)
            prefs[Keys.SPEED_UNIT] = settings.speedUnit.name
            prefs[Keys.WIDGET_ITEMS] = settings.enabledWidgetFeatures.map { it.name.lowercase() }.toSet()
            prefs[Keys.WIDGET_SEEN_DEFAULTS] = DEFAULT_WIDGET_ITEMS
            prefs[Keys.MAP_FAB_ITEMS] = settings.enabledMapFeatures.map { it.name.lowercase() }.toSet()
            prefs[Keys.MAP_FAB_SEEN_DEFAULTS] = DEFAULT_MAP_FAB_ITEMS
            prefs[Keys.FEATURE_ORDER] = settings.featureOrder.serializeFeatureOrder()
            prefs[Keys.ENABLED_SPEED_PROFILE_IDS] = settings.enabledSpeedProfileIds
            prefs[Keys.REMEMBER_LAST_LOCATION] = settings.rememberLastLocation
            prefs[Keys.MAP_FOLLOWS_LOCATION] = settings.mapFollowsLocation
            prefs[Keys.MAP_TILE_SOURCE] = settings.mapTileSource.name
            prefs[Keys.GUIDED_PATH] = settings.guidedPath.name
            prefs[Keys.DISABLED_GEOCODING_PROVIDERS] = settings.disabledGeocodingProviders.map { it.name }.toSet()
            prefs[Keys.JITTER_IDLE_RADIUS_METERS] = snapshot.jitterIdleRadius.coerceIn(0.0, MAX_JITTER_RADIUS_METERS)
            prefs[Keys.JITTER_MOVING_RADIUS_METERS] = snapshot.jitterMovingRadius.coerceIn(0.0, MAX_JITTER_RADIUS_METERS)
            prefs[Keys.JITTER_MAX_STEP_METERS] =
                snapshot.jitterMaxStepMeters.coerceIn(MIN_JITTER_STEP_METERS, MAX_JITTER_STEP_METERS)
            prefs[Keys.REALISM_BEARING_HOLD_IDLE] = settings.bearingHoldOnIdle
            prefs[Keys.REALISM_ALTITUDE_ENABLED] = settings.altitudeEnabled
            prefs[Keys.REALISM_WARMUP_ENABLED] = settings.warmupEnabled
            prefs[Keys.REALISM_SATELLITE_EXTRAS_ENABLED] = settings.satelliteExtrasEnabled
            prefs[Keys.REALISM_SUSPENDED_MOCKING_ENABLED] = settings.suspendedMockingEnabled
            prefs[Keys.HIDE_TELEPORT_FEATURES] = settings.hideTeleportFeatures
            prefs[Keys.HIDE_WIDGET_OVERLAY] = settings.hideWidgetOverlay
            prefs[Keys.HIDE_FOREGROUND_NOTIFICATION] = settings.hideForegroundNotification
            prefs[Keys.SHOW_ROUTE_JUMP_BUTTONS] = settings.showRouteJumpButtons
            prefs[Keys.LAST_KNOWN_POSITION_ENABLED] = settings.lastKnownPositionEnabled
            prefs[Keys.AGPS_RESET_ON_START_ENABLED] = settings.agpsResetOnStartEnabled
            prefs[Keys.BYPASS_MOCK_LOCATION_CHECK] = settings.bypassMockLocationCheck
            prefs[Keys.REALISM_REAL_ELEVATION_ENABLED] = settings.realElevationEnabled
            prefs[Keys.ALTITUDE_JITTER_RADIUS_METERS] =
                settings.altitudeJitterRadiusMeters.coerceIn(0.0, MAX_JITTER_RADIUS_METERS)
            prefs[Keys.PLANTING_RADIUS_METERS] = clampPlantingRadius(settings.plantingRadiusMeters)
            prefs[Keys.ALTITUDE_OVERRIDE_BUTTON_ENABLED] = settings.altitudeOverrideButtonEnabled
            prefs[Keys.DEBUG_STATS_ENABLED] = settings.debugStatsEnabled
            prefs[Keys.JITTER_SPEED_IDLE_VARIATION_PCT] = snapshot.jitterSpeedIdleVariationPct.coercePct()
            prefs[Keys.JITTER_SPEED_MOVING_VARIATION_PCT] = snapshot.jitterSpeedMovingVariationPct.coercePct()
            prefs[Keys.JITTER_SPEED_IDLE_WOBBLE_PROBABILITY_PCT] = snapshot.jitterSpeedIdleWobbleProbabilityPct.coercePct()
            prefs[Keys.HOT_LOCATIONS_ENABLED] = snapshot.hotLocationsEnabled
            prefs[Keys.HOT_LOCATION_SELECTED_IDS] = snapshot.selectedHotLocationIds
            prefs[Keys.HOT_ROUTES_ENABLED] = snapshot.hotRoutesEnabled
            prefs[Keys.HOT_ROUTE_SELECTED_IDS] = snapshot.selectedHotRouteIds
            prefs[Keys.FLOATING_MAP_QUICK_WALK] = settings.floatingMapQuickWalk
            prefs.writeRoamingDefaults(settings.roamingDefaults)
        }
    }
}

private fun Int.coercePct(): Int =
    coerceIn(AppConstants.JitterConstants.SPEED_VARIATION_PCT_MIN, AppConstants.JitterConstants.SPEED_VARIATION_PCT_MAX)

private fun Preferences.toSettingsSnapshot(): SettingsSnapshot {
    val prefs = this
    return SettingsSnapshot(
        settings =
            AppSettings(
                activeSpeedProfileId = prefs[Keys.ACTIVE_PROFILE_ID] ?: AppConstants.ProfileConstants.DEFAULT_ACTIVE_PROFILE_ID,
                enabledSpeedProfileIds = prefs[Keys.ENABLED_SPEED_PROFILE_IDS] ?: DEFAULT_ENABLED_SPEED_PROFILE_IDS,
                featureOrder = parseFeatureOrder(prefs[Keys.FEATURE_ORDER]),
                enabledWidgetFeatures = resolveWidgetItems(prefs).mapNotNull { it.toAppFeature() }.toSet(),
                enabledMapFeatures = resolveMapFabItems(prefs).mapNotNull { it.toAppFeature() }.toSet(),
                mapFollowsLocation = prefs[Keys.MAP_FOLLOWS_LOCATION] ?: true,
                mapTileSource = MapTileSource.fromName(prefs[Keys.MAP_TILE_SOURCE]),
                guidedPath = GuidedPath.fromName(prefs[Keys.GUIDED_PATH]),
                disabledGeocodingProviders =
                    prefs[Keys.DISABLED_GEOCODING_PROVIDERS].orEmpty().mapNotNull(GeocodingProviderId::fromName).toSet(),
                speedUnit =
                    SpeedUnit.entries.firstOrNull { it.name == prefs[Keys.SPEED_UNIT] } ?: SpeedUnit.KMH,
                roamingDefaults = prefs.toRoamingDefaults(),
                bearingHoldOnIdle = prefs[Keys.REALISM_BEARING_HOLD_IDLE] ?: true,
                altitudeEnabled = prefs[Keys.REALISM_ALTITUDE_ENABLED] ?: true,
                warmupEnabled = prefs[Keys.REALISM_WARMUP_ENABLED] ?: false,
                satelliteExtrasEnabled = prefs[Keys.REALISM_SATELLITE_EXTRAS_ENABLED] ?: true,
                suspendedMockingEnabled = prefs[Keys.REALISM_SUSPENDED_MOCKING_ENABLED] ?: false,
                hideTeleportFeatures = prefs[Keys.HIDE_TELEPORT_FEATURES] ?: false,
                hideWidgetOverlay = prefs[Keys.HIDE_WIDGET_OVERLAY] ?: false,
                hideForegroundNotification = prefs[Keys.HIDE_FOREGROUND_NOTIFICATION] ?: false,
                showRouteJumpButtons =
                    prefs[Keys.SHOW_ROUTE_JUMP_BUTTONS] ?: AppConstants.ProfileConstants.SHOW_ROUTE_JUMP_BUTTONS_DEFAULT,
                lastKnownPositionEnabled =
                    prefs[Keys.LAST_KNOWN_POSITION_ENABLED] ?: AppConstants.ProfileConstants.LAST_KNOWN_POSITION_ENABLED_DEFAULT,
                agpsResetOnStartEnabled =
                    prefs[Keys.AGPS_RESET_ON_START_ENABLED] ?: AppConstants.ProfileConstants.AGPS_RESET_ON_START_ENABLED_DEFAULT,
                bypassMockLocationCheck = prefs[Keys.BYPASS_MOCK_LOCATION_CHECK] ?: false,
                realElevationEnabled =
                    prefs[Keys.REALISM_REAL_ELEVATION_ENABLED] ?: AppConstants.RealismConstants.REAL_ELEVATION_ENABLED_DEFAULT,
                altitudeJitterRadiusMeters = prefs[Keys.ALTITUDE_JITTER_RADIUS_METERS] ?: DEFAULT_ALTITUDE_JITTER_RADIUS_METERS,
                plantingRadiusMeters = prefs[Keys.PLANTING_RADIUS_METERS] ?: DEFAULT_PLANTING_RADIUS_METERS,
                altitudeOverrideButtonEnabled = prefs[Keys.ALTITUDE_OVERRIDE_BUTTON_ENABLED] ?: false,
                debugStatsEnabled = prefs[Keys.DEBUG_STATS_ENABLED] ?: false,
                rememberLastLocation =
                    prefs[Keys.REMEMBER_LAST_LOCATION] ?: AppConstants.DataStoreConstants.DEFAULT_REMEMBER_LAST_LOCATION,
                floatingMapQuickWalk = prefs[Keys.FLOATING_MAP_QUICK_WALK] ?: false,
                tapToWalkOverlayEnabled = prefs[Keys.TAP_TO_WALK_OVERLAY_ENABLED] ?: false,
                tapToWalkMoves = prefs.toTapToWalkMoves(),
                reconnectTapEnabled = prefs[Keys.RECONNECT_TAP_ENABLED] ?: false,
                keepWidgetOnIdle = prefs[Keys.KEEP_WIDGET_ON_IDLE] ?: false,
                themeMode =
                    runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: AppConstants.DataStoreConstants.DEFAULT_THEME_MODE) }
                        .getOrDefault(ThemeMode.DARK),
            ),
        slowWalkSpeedMs = prefs[Keys.SLOW_WALK_SPEED_MS] ?: DEFAULT_SLOW_WALK_SPEED_MS,
        walkSpeedMs = prefs[Keys.WALK_SPEED_MS] ?: DEFAULT_WALK_SPEED_MS,
        runSpeedMs = prefs[Keys.RUN_SPEED_MS] ?: DEFAULT_RUN_SPEED_MS,
        bikeSpeedMs = prefs[Keys.BIKE_SPEED_MS] ?: DEFAULT_BIKE_SPEED_MS,
        driveSpeedMs = prefs[Keys.DRIVE_SPEED_MS] ?: DEFAULT_DRIVE_SPEED_MS,
        jitterIdleRadius = prefs[Keys.JITTER_IDLE_RADIUS_METERS] ?: DEFAULT_JITTER_IDLE_RADIUS_METERS,
        jitterMovingRadius = prefs[Keys.JITTER_MOVING_RADIUS_METERS] ?: DEFAULT_JITTER_MOVING_RADIUS_METERS,
        jitterMaxStepMeters = prefs[Keys.JITTER_MAX_STEP_METERS] ?: DEFAULT_JITTER_MAX_STEP_METERS,
        jitterSpeedIdleVariationPct = prefs[Keys.JITTER_SPEED_IDLE_VARIATION_PCT] ?: DEFAULT_JITTER_SPEED_IDLE_VARIATION_PCT,
        jitterSpeedMovingVariationPct = prefs[Keys.JITTER_SPEED_MOVING_VARIATION_PCT] ?: DEFAULT_JITTER_SPEED_MOVING_VARIATION_PCT,
        jitterSpeedIdleWobbleProbabilityPct =
            prefs[Keys.JITTER_SPEED_IDLE_WOBBLE_PROBABILITY_PCT] ?: DEFAULT_JITTER_SPEED_IDLE_WOBBLE_PROBABILITY_PCT,
        hotLocationsEnabled = prefs[Keys.HOT_LOCATIONS_ENABLED] ?: false,
        selectedHotLocationIds = prefs[Keys.HOT_LOCATION_SELECTED_IDS] ?: emptySet(),
        hotRoutesEnabled = prefs[Keys.HOT_ROUTES_ENABLED] ?: false,
        selectedHotRouteIds = prefs[Keys.HOT_ROUTE_SELECTED_IDS] ?: emptySet(),
    )
}

internal fun resolveWidgetItems(prefs: Preferences): Set<String> {
    val stored = prefs[Keys.WIDGET_ITEMS] ?: DEFAULT_WIDGET_ITEMS
    return mergeNewDefaultWidgetFeatures(stored, prefs[Keys.WIDGET_SEEN_DEFAULTS])
}

internal fun resolveMapFabItems(prefs: Preferences): Set<String> {
    val stored = prefs[Keys.MAP_FAB_ITEMS] ?: DEFAULT_MAP_FAB_ITEMS
    return mergeNewDefaultMapFeatures(stored, prefs[Keys.MAP_FAB_SEEN_DEFAULTS])
}

internal fun Preferences.toRoamingDefaults(): RoamingDefaults =
    RoamingDefaults(
        radiusMeters = this[Keys.ROAMING_RADIUS_METERS] ?: DEFAULT_ROAMING_RADIUS_METERS,
        distanceMeters = this[Keys.ROAMING_DISTANCE_METERS] ?: DEFAULT_ROAMING_DISTANCE_METERS,
        speedProfileId = this[Keys.ROAMING_SPEED_PROFILE_ID] ?: DEFAULT_ROAMING_SPEED_PROFILE_ID,
        followRoads = this[Keys.ROAMING_ROAD_FOLLOWING] ?: DEFAULT_ROAMING_FOLLOW_ROADS,
        returnToInitialLocation = this[Keys.ROAMING_RETURN_TO_START] ?: DEFAULT_ROAMING_RETURN_TO_START,
        kind = RoamingKind.parse(this[Keys.ROAMING_KIND] ?: DEFAULT_ROAMING_KIND),
        plantingStartRadiusMeters =
            this[Keys.ROAMING_PLANTING_START_RADIUS_METERS] ?: DEFAULT_ROAMING_PLANTING_START_RADIUS_METERS,
        plantingEndRadiusMeters =
            this[Keys.ROAMING_PLANTING_END_RADIUS_METERS] ?: DEFAULT_ROAMING_PLANTING_END_RADIUS_METERS,
        plantingInfiniteLoops =
            this[Keys.ROAMING_PLANTING_INFINITE_LOOPS] ?: DEFAULT_ROAMING_PLANTING_INFINITE_LOOPS,
        plantingLoopCount =
            this[Keys.ROAMING_PLANTING_LOOP_COUNT] ?: DEFAULT_ROAMING_PLANTING_LOOP_COUNT,
        plantingSpacingMeters =
            this[Keys.ROAMING_PLANTING_SPACING_METERS] ?: DEFAULT_ROAMING_PLANTING_SPACING_METERS,
        plantingExpandOnly =
            this[Keys.ROAMING_PLANTING_EXPAND_ONLY] ?: DEFAULT_ROAMING_PLANTING_EXPAND_ONLY,
        plantingSpeedProfileId =
            this[Keys.ROAMING_PLANTING_SPEED_PROFILE_ID] ?: DEFAULT_ROAMING_PLANTING_SPEED_PROFILE_ID,
    )

internal fun MutablePreferences.writeRoamingDefaults(defaults: RoamingDefaults) {
    this[Keys.ROAMING_RADIUS_METERS] = defaults.radiusMeters
    this[Keys.ROAMING_DISTANCE_METERS] = defaults.distanceMeters
    this[Keys.ROAMING_SPEED_PROFILE_ID] = defaults.speedProfileId
    this[Keys.ROAMING_ROAD_FOLLOWING] = defaults.followRoads
    this[Keys.ROAMING_RETURN_TO_START] = defaults.returnToInitialLocation
    this[Keys.ROAMING_KIND] = defaults.kind.name
    this[Keys.ROAMING_PLANTING_START_RADIUS_METERS] = defaults.plantingStartRadiusMeters
    this[Keys.ROAMING_PLANTING_END_RADIUS_METERS] = defaults.plantingEndRadiusMeters
    this[Keys.ROAMING_PLANTING_INFINITE_LOOPS] = defaults.plantingInfiniteLoops
    this[Keys.ROAMING_PLANTING_LOOP_COUNT] = defaults.plantingLoopCount
    this[Keys.ROAMING_PLANTING_SPACING_METERS] = defaults.plantingSpacingMeters
    this[Keys.ROAMING_PLANTING_EXPAND_ONLY] = defaults.plantingExpandOnly
    this[Keys.ROAMING_PLANTING_SPEED_PROFILE_ID] = defaults.plantingSpeedProfileId
}
