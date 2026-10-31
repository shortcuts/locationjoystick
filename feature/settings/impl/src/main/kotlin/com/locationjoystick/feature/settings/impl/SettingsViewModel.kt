package com.locationjoystick.feature.settings.impl

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.root.SensorPermissionBootstrap
import com.locationjoystick.core.common.util.LocaleContextWrapper
import com.locationjoystick.core.common.util.NetworkUtils
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.common.util.RandomCode
import com.locationjoystick.core.data.BackupRepository
import com.locationjoystick.core.data.CaptureCoordinatesRepository
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.HotLocationsRepository
import com.locationjoystick.core.data.HotRoutesRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.datastore.SettingsSnapshot
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.ExportData
import com.locationjoystick.core.model.GeocodingProviderId
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.ThemeMode
import com.locationjoystick.feature.settings.impl.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class InstalledApp(
    val packageName: String,
    val label: String,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val captureCoordinatesRepository: CaptureCoordinatesRepository,
        private val favoriteRepository: FavoriteRepository,
        private val hotLocationsRepository: HotLocationsRepository,
        private val hotRoutesRepository: HotRoutesRepository,
        private val routeRepository: RouteRepository,
        private val backupRepository: BackupRepository,
        private val sensorPermissionBootstrap: SensorPermissionBootstrap,
        private val importExportRepository: ImportExportRepository,
        private val exportSyncServer: ExportSyncServer,
        private val exportSyncClient: ExportSyncClient,
        private val nsdCodeManager: NsdCodeManager,
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        companion object {
            private const val TAG = "SettingsViewModel"
            private const val AUTOSAVE_DEBOUNCE_MS = 250L

            fun convertMsToDisplay(
                ms: Double,
                unit: SpeedUnit,
            ): Double =
                when (unit) {
                    SpeedUnit.KMH -> ms * 3.6
                    SpeedUnit.MPH -> ms * 2.237
                }
        }

        private val _isRooted = MutableStateFlow(false)
        val isRooted: StateFlow<Boolean> = _isRooted.asStateFlow()

        private val _languageTag = MutableStateFlow(LocaleContextWrapper.getLanguage(context))
        val languageTag: StateFlow<String?> = _languageTag.asStateFlow()

        fun setLanguage(tag: String?) {
            LocaleContextWrapper.setLanguage(context, tag)
            _languageTag.value = tag
        }

        private val screenServiceGranted = MutableStateFlow(false)

        /** Live mirror of [CaptureCoordinatesRepository] — same source the top-level Capture screen reads. */
        private data class CaptureSnapshot(
            val modeEnabled: Boolean,
            val captureEnabled: Boolean,
            val jumpEnabled: Boolean,
            val previousBrowserPackage: String?,
            val launchAfterLinkPackage: String?,
            val launchAfterLinkChosen: Boolean,
            val setupReset: Boolean,
        )

        private data class CaptureToggles(
            val modeEnabled: Boolean,
            val captureEnabled: Boolean,
            val jumpEnabled: Boolean,
        )

        private data class CaptureLinkSetup(
            val previousBrowserPackage: String?,
            val launchAfterLinkPackage: String?,
            val launchAfterLinkChosen: Boolean,
            val setupReset: Boolean,
        )

        private val captureSnapshot: Flow<CaptureSnapshot> =
            combine(
                combine(
                    captureCoordinatesRepository.captureModeEnabled,
                    captureCoordinatesRepository.captureEnabled,
                    captureCoordinatesRepository.jumpEnabled,
                    ::CaptureToggles,
                ),
                combine(
                    captureCoordinatesRepository.previousBrowserPackage,
                    captureCoordinatesRepository.launchAfterLinkPackage,
                    captureCoordinatesRepository.launchAfterLinkChosen,
                    captureCoordinatesRepository.setupReset,
                    ::CaptureLinkSetup,
                ),
            ) { toggles, link ->
                CaptureSnapshot(
                    modeEnabled = toggles.modeEnabled,
                    captureEnabled = toggles.captureEnabled,
                    jumpEnabled = toggles.jumpEnabled,
                    previousBrowserPackage = link.previousBrowserPackage,
                    launchAfterLinkPackage = link.launchAfterLinkPackage,
                    launchAfterLinkChosen = link.launchAfterLinkChosen,
                    setupReset = link.setupReset,
                )
            }

        private val writeMutex = Mutex()

        private val speedWriteVersion = mutableMapOf<String, Int>()

        init {
            _isRooted.value = sensorPermissionBootstrap.isGranted()
        }

        internal data class UserFeedback(
            val message: String,
            val isError: Boolean = false,
        )

        private val _qrImportReady = MutableSharedFlow<ExportData>(extraBufferCapacity = 1)
        val qrImportReady: SharedFlow<ExportData> = _qrImportReady

        private val _qrImportFetching = MutableStateFlow(false)
        val qrImportFetching: StateFlow<Boolean> = _qrImportFetching.asStateFlow()

        private val _isPreparingQrExport = MutableStateFlow(false)
        val isPreparingQrExport: StateFlow<Boolean> = _isPreparingQrExport.asStateFlow()

        /** [qrText] renders as the QR; [code] is the same session typeable manually on the other device. */
        internal data class QrExportSession(
            val qrText: String,
            val code: String,
        )

        internal val qrExportReady = MutableSharedFlow<QrExportSession>(extraBufferCapacity = 1)

        internal val userFeedback = MutableSharedFlow<UserFeedback>(extraBufferCapacity = 1)

        /** Emits a localized [UserFeedback]; [args] are format arguments for [id]. */
        private suspend fun emitFeedback(
            @StringRes id: Int,
            vararg args: Any,
            isError: Boolean = false,
        ) {
            val message = if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
            userFeedback.emit(UserFeedback(message, isError))
        }

        private suspend fun emitQuantityFeedback(
            @PluralsRes id: Int,
            count: Int,
            isError: Boolean = false,
        ) {
            userFeedback.emit(UserFeedback(context.resources.getQuantityString(id, count, count), isError))
        }

        /** Runs [block] to completion even if the screen closes, one write at a time. */
        private fun save(block: suspend () -> Unit) {
            viewModelScope.launch { persist(block) }
        }

        private suspend fun persist(block: suspend () -> Unit) {
            withContext(NonCancellable) {
                writeMutex.withLock {
                    try {
                        block()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Save failed", e)
                        emitFeedback(R.string.settings_viewmodel_failed_to_save_settings, isError = true)
                    }
                }
            }
        }

        private data class LiveSettings(
            val disclosureChoice: String,
            val reconnectTap: Boolean,
            val keepWidgetOnIdle: Boolean,
            val activeSpeedProfileId: String,
            val tapToWalk: Boolean,
        )

        private val snapshotFlow = settingsRepository.getSettingsSnapshot()

        val roamingDefaults: StateFlow<RoamingDefaults> =
            settingsRepository
                .getRoamingDefaults()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = RoamingDefaults(),
                )

        val uiState: StateFlow<SettingsUiState> =
            combine(
                snapshotFlow,
                combine(
                    settingsRepository.getAccessibilityDisclosureChoice(),
                    settingsRepository.getReconnectTapEnabled(),
                    settingsRepository.getKeepWidgetOnIdle(),
                    settingsRepository.getActiveSpeedProfile().map { it.id },
                    settingsRepository.getTapToWalkOverlayEnabled(),
                    ::LiveSettings,
                ),
                combine(screenServiceGranted, captureSnapshot) { granted, capture -> Pair(granted, capture) },
                settingsRepository.getThemeMode(),
                settingsRepository.getBaseAltitudeOverride(),
            ) {
                snapshot,
                live,
                (isServiceGranted, capture),
                themeMode,
                baseAltitudeOverride,
                ->
                SettingsUiState(
                    isLoading = false,
                    speeds = snapshotSpeeds(snapshot),
                    speedUnit = snapshot.speedUnit,
                    featureOrder = snapshot.featureOrder,
                    enabledWidgetFeatures = snapshot.enabledWidgetFeatures,
                    enabledSpeedProfileIds = snapshot.enabledSpeedProfileIds,
                    enabledMapFeatures = snapshot.enabledMapFeatures,
                    rememberLastLocation = snapshot.rememberLastLocation,
                    mapFollowsLocation = snapshot.mapFollowsLocation,
                    mapTileSource = snapshot.mapTileSource,
                    guidedPath = snapshot.guidedPath,
                    disabledGeocodingProviders = snapshot.disabledGeocodingProviders,
                    jitterIdleRadiusMeters = snapshot.jitterIdleRadius,
                    jitterMovingRadiusMeters = snapshot.jitterMovingRadius,
                    jitterMaxStepMeters = snapshot.jitterMaxStepMeters,
                    realismBearingHoldIdle = snapshot.realismBearingHoldIdle,
                    realismAltitudeEnabled = snapshot.realismAltitudeEnabled,
                    realismWarmupEnabled = snapshot.realismWarmupEnabled,
                    realismSatelliteExtrasEnabled = snapshot.realismSatelliteExtrasEnabled,
                    realismSuspendedMockingEnabled = snapshot.realismSuspendedMockingEnabled,
                    jitterSpeedIdleVariationPct = snapshot.jitterSpeedIdleVariationPct,
                    jitterSpeedMovingVariationPct = snapshot.jitterSpeedMovingVariationPct,
                    jitterSpeedIdleWobbleProbabilityPct = snapshot.jitterSpeedIdleWobbleProbabilityPct,
                    hotLocationsEnabled = snapshot.hotLocationsEnabled,
                    selectedHotLocationIds = snapshot.selectedHotLocationIds,
                    hotRoutesEnabled = snapshot.hotRoutesEnabled,
                    selectedHotRouteIds = snapshot.selectedHotRouteIds,
                    floatingMapQuickWalk = snapshot.floatingMapQuickWalk,
                    tapToWalkOverlayEnabled = live.tapToWalk,
                    hideTeleportFeatures = snapshot.hideTeleportFeatures,
                    hideWidgetOverlay = snapshot.hideWidgetOverlay,
                    hideForegroundNotification = snapshot.hideForegroundNotification,
                    showRouteJumpButtons = snapshot.showRouteJumpButtons,
                    lastKnownPositionEnabled = snapshot.lastKnownPositionEnabled,
                    realismRealElevationEnabled = snapshot.realismRealElevationEnabled,
                    hasAltitudeOverride = baseAltitudeOverride != null,
                    altitudeJitterRadiusMeters = snapshot.altitudeJitterRadiusMeters,
                    plantingRadiusMeters = snapshot.plantingRadiusMeters,
                    altitudeOverrideButtonEnabled = snapshot.altitudeOverrideButtonEnabled,
                    debugStatsEnabled = snapshot.debugStatsEnabled,
                    captureModeEnabled = capture.modeEnabled,
                    captureEnabled = capture.captureEnabled,
                    jumpEnabled = capture.jumpEnabled,
                    capturePreviousBrowserPackage = capture.previousBrowserPackage,
                    captureSetupReset = capture.setupReset,
                    launchAfterLinkPackage = capture.launchAfterLinkPackage,
                    launchAfterLinkChosen = capture.launchAfterLinkChosen,
                    isScreenServiceGranted = isServiceGranted,
                    accessibilityDisclosureAccepted =
                        live.disclosureChoice == AppConstants.AccessibilityConstants.DISCLOSURE_ACCEPTED,
                    reconnectTapEnabled = live.reconnectTap,
                    keepWidgetOnIdle = live.keepWidgetOnIdle,
                    bypassMockLocationCheck = snapshot.bypassMockLocationCheck,
                    activeSpeedProfileId = live.activeSpeedProfileId,
                    themeMode = themeMode,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(isLoading = true),
            )

        fun setSpeed(
            id: String,
            displaySpeed: Double,
        ) {
            val speedMs = convertDisplayToMs(displaySpeed, uiState.value.speedUnit)
            val version = (speedWriteVersion[id] ?: 0) + 1
            speedWriteVersion[id] = version
            // Each keystroke calls this; writing "1" while the user types "15" would set the live pace to 1 km/h.
            viewModelScope.launch {
                withContext(NonCancellable) {
                    delay(AUTOSAVE_DEBOUNCE_MS)
                    if (speedWriteVersion[id] == version) persist { writeSpeed(id, speedMs) }
                }
            }
        }

        private suspend fun writeSpeed(
            id: String,
            speedMs: Double,
        ) {
            when (id) {
                "slow_walk" -> settingsRepository.setSlowWalkSpeed(speedMs)
                "walk" -> settingsRepository.setWalkSpeed(speedMs)
                "run" -> settingsRepository.setRunSpeed(speedMs)
                "bike" -> settingsRepository.setBikeSpeed(speedMs)
                "drive" -> settingsRepository.setDriveSpeed(speedMs)
            }
        }

        fun setSpeedUnit(unit: SpeedUnit) {
            save { settingsRepository.setSpeedUnit(unit) }
        }

        fun setWidgetFeatures(features: Set<AppFeature>) {
            save { settingsRepository.setWidgetFeatures(features) }
        }

        /** No-op if [ids] is empty — at least one speed profile must stay enabled for cycling to work. */
        fun setEnabledSpeedProfileIds(ids: Set<String>) {
            if (ids.isEmpty()) return
            save { settingsRepository.setEnabledSpeedProfileIds(ids) }
        }

        fun setMapFeatures(features: Set<AppFeature>) {
            save { settingsRepository.setMapFeatures(features) }
        }

        fun setFeatureOrder(order: List<AppFeature>) {
            save { settingsRepository.setFeatureOrder(order) }
        }

        fun setRememberLastLocation(enabled: Boolean) {
            save { settingsRepository.setRememberLastLocation(enabled) }
        }

        fun setMapFollowsLocation(enabled: Boolean) {
            save { settingsRepository.setMapFollowsLocation(enabled) }
        }

        /** No-op if [ids] disables every provider — at least one geocoding provider must stay on. */
        fun setDisabledGeocodingProviders(ids: Set<GeocodingProviderId>) {
            if (ids.containsAll(GeocodingProviderId.entries)) return
            save { settingsRepository.setDisabledGeocodingProviders(ids) }
        }

        fun setMapTileSource(source: MapTileSource) {
            save { settingsRepository.setMapTileSource(source) }
        }

        fun setJitterIdleRadius(meters: Double) {
            save { settingsRepository.setJitterIdleRadius(meters) }
        }

        fun setJitterMovingRadius(meters: Double) {
            save { settingsRepository.setJitterMovingRadius(meters) }
        }

        fun setJitterMaxStepMeters(meters: Double) {
            save { settingsRepository.setJitterMaxStepMeters(meters) }
        }

        fun updateRoamingDefaults(defaults: RoamingDefaults) {
            save { settingsRepository.updateRoamingDefaults(defaults) }
        }

        fun setRealismBearingHoldIdle(v: Boolean) {
            save { settingsRepository.setRealismBearingHoldIdle(v) }
        }

        fun setRealismAltitudeEnabled(v: Boolean) {
            save { settingsRepository.setRealismAltitudeEnabled(v) }
        }

        fun setRealismWarmupEnabled(v: Boolean) {
            save { settingsRepository.setRealismWarmupEnabled(v) }
        }

        fun setRealismSatelliteExtrasEnabled(v: Boolean) {
            save { settingsRepository.setRealismSatelliteExtrasEnabled(v) }
        }

        fun setRealismSuspendedMockingEnabled(v: Boolean) {
            save { settingsRepository.setRealismSuspendedMockingEnabled(v) }
        }

        fun setJitterSpeedIdleVariationPct(pct: Int) {
            save { settingsRepository.setJitterSpeedIdleVariationPct(pct) }
        }

        fun setJitterSpeedMovingVariationPct(pct: Int) {
            save { settingsRepository.setJitterSpeedMovingVariationPct(pct) }
        }

        fun setJitterSpeedIdleWobbleProbabilityPct(pct: Int) {
            save { settingsRepository.setJitterSpeedIdleWobbleProbabilityPct(pct) }
        }

        fun setHotLocationsEnabled(enabled: Boolean) {
            val allIds =
                hotLocationsRepository.locations.value
                    .map { FavoriteRepository.idForLocation(it.name, it.city) }
                    .toSet()
            save {
                if (enabled &&
                    settingsRepository
                        .getSettingsSnapshot()
                        .first()
                        .selectedHotLocationIds
                        .isEmpty()
                ) {
                    settingsRepository.setSelectedHotLocationIds(allIds)
                }
                settingsRepository.setHotLocationsEnabled(enabled)
                syncHotLocations()
            }
        }

        fun setSelectedHotLocationIds(ids: Set<String>) {
            save {
                settingsRepository.setSelectedHotLocationIds(ids)
                syncHotLocations()
            }
        }

        private suspend fun syncHotLocations() {
            val saved = settingsRepository.getSettingsSnapshot().first()
            if (saved.hotLocationsEnabled) {
                favoriteRepository.upsertHotLocations(hotLocationsRepository.locations.value, saved.selectedHotLocationIds)
            } else {
                favoriteRepository.removeHotLocations()
            }
            clearHomeIfMissing()
        }

        val hotLocationTree: StateFlow<HotItemTree> =
            hotLocationsRepository.locations
                .map { locations ->
                    HotItemTree(
                        allIds = locations.map { FavoriteRepository.idForLocation(it.name, it.city) }.toSet(),
                        byCountry =
                            locations.groupBy { it.country }.mapValues { (_, locs) ->
                                locs.groupBy { it.city }.mapValues { (_, items) ->
                                    items.map { HotItemEntry(FavoriteRepository.idForLocation(it.name, it.city), it.name) }
                                }
                            },
                    )
                }.stateIn(viewModelScope, SharingStarted.Eagerly, HotItemTree.Empty)

        fun setHotRoutesEnabled(enabled: Boolean) {
            val allIds =
                hotRoutesRepository.routes.value
                    .map { RouteRepository.idForRoute(it.name, it.city) }
                    .toSet()
            save {
                if (enabled &&
                    settingsRepository
                        .getSettingsSnapshot()
                        .first()
                        .selectedHotRouteIds
                        .isEmpty()
                ) {
                    settingsRepository.setSelectedHotRouteIds(allIds)
                }
                settingsRepository.setHotRoutesEnabled(enabled)
                syncHotRoutes()
            }
        }

        fun setSelectedHotRouteIds(ids: Set<String>) {
            save {
                settingsRepository.setSelectedHotRouteIds(ids)
                syncHotRoutes()
            }
        }

        private suspend fun syncHotRoutes() {
            val saved = settingsRepository.getSettingsSnapshot().first()
            if (saved.hotRoutesEnabled) {
                routeRepository.upsertHotRoutes(hotRoutesRepository.routes.value, saved.selectedHotRouteIds)
            } else {
                routeRepository.removeHotRoutes()
            }
        }

        fun setFloatingMapQuickWalk(enabled: Boolean) {
            save { settingsRepository.setFloatingMapQuickWalk(enabled) }
        }

        fun setHideTeleportFeatures(enabled: Boolean) {
            save { settingsRepository.setHideTeleportFeatures(enabled) }
        }

        fun setHideWidgetOverlay(enabled: Boolean) {
            save { settingsRepository.setHideWidgetOverlay(enabled) }
        }

        fun setHideForegroundNotification(enabled: Boolean) {
            save { settingsRepository.setHideForegroundNotification(enabled) }
        }

        fun setShowRouteJumpButtons(enabled: Boolean) {
            save { settingsRepository.setShowRouteJumpButtons(enabled) }
        }

        fun setLastKnownPositionEnabled(enabled: Boolean) {
            save { settingsRepository.setLastKnownPositionEnabled(enabled) }
        }

        fun setRealismRealElevationEnabled(enabled: Boolean) {
            save { settingsRepository.setRealismRealElevationEnabled(enabled) }
        }

        /** Clears the manual altitude override immediately immediately. */
        fun resetAltitudeOverride() {
            save { settingsRepository.clearBaseAltitudeOverride() }
        }

        fun setAltitudeJitterRadius(meters: Double) {
            save { settingsRepository.setAltitudeJitterRadius(meters) }
        }

        fun setPlantingRadius(meters: Double) {
            save { settingsRepository.setPlantingRadius(meters) }
        }

        fun setAltitudeOverrideButtonEnabled(enabled: Boolean) {
            save { settingsRepository.setAltitudeOverrideButtonEnabled(enabled) }
        }

        fun setDebugStatsEnabled(enabled: Boolean) {
            save { settingsRepository.setDebugStatsEnabled(enabled) }
        }

        /** Same [CaptureCoordinatesRepository] the top-level Capture screen writes. */
        fun setCaptureModeEnabled(enabled: Boolean) {
            save { captureCoordinatesRepository.setCaptureModeEnabled(enabled) }
        }

        fun setCaptureEnabled(enabled: Boolean) {
            save { captureCoordinatesRepository.setCaptureEnabled(enabled) }
        }

        fun setJumpEnabled(enabled: Boolean) {
            save { captureCoordinatesRepository.setJumpEnabled(enabled) }
        }

        fun setCapturePreviousBrowserPackage(packageName: String) {
            save { captureCoordinatesRepository.setPreviousBrowserPackage(packageName) }
        }

        fun restoreCaptureDefaultBrowser() {
            save { captureCoordinatesRepository.resetSetup() }
        }

        fun clearCaptureSetupReset() {
            save { captureCoordinatesRepository.clearSetupReset() }
        }

        /** A reset only holds while this app still owns the browser role; once it does not, setup is due anyway. */
        fun onCaptureDefaultBrowserChecked(isDefault: Boolean) {
            if (!isDefault && uiState.value.captureSetupReset) clearCaptureSetupReset()
        }

        fun setLaunchAfterLinkPackage(packageName: String?) {
            save { captureCoordinatesRepository.setLaunchAfterLinkPackage(packageName) }
        }

        fun setTapToWalkOverlayEnabled(enabled: Boolean) {
            save { settingsRepository.setTapToWalkOverlayEnabled(enabled) }
        }

        fun setAccessibilityDisclosureAccepted(accepted: Boolean) {
            save { settingsRepository.recordAccessibilityDisclosure(accepted) }
        }

        fun setReconnectTapEnabled(enabled: Boolean) {
            save { settingsRepository.setReconnectTapEnabled(enabled) }
        }

        /** Launchable apps for the Capture launch-after-link picker; empty until the off-main query finishes. */
        internal val launchableApps: StateFlow<List<InstalledApp>> =
            flow { emit(queryLaunchableApps()) }
                .flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        // The app manifest <queries> block declares MAIN/LAUNCHER; library lint cannot see it.
        @SuppressLint("QueryPermissionsNeeded")
        private fun queryLaunchableApps(): List<InstalledApp> {
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            return pm
                .queryIntentActivities(launcherIntent, 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }

        fun setGuidedPath(path: GuidedPath) {
            save { settingsRepository.setGuidedPath(path) }
        }

        fun setThemeMode(mode: ThemeMode) {
            save { settingsRepository.setThemeMode(mode) }
        }

        fun checkScreenServiceGranted() {
            val am = context.getSystemService(AccessibilityManager::class.java)
            screenServiceGranted.value =
                am
                    ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    ?.any { it.id.contains("ScreenAccessibilityService") }
                    ?: false
        }

        val hotRouteTree: StateFlow<HotItemTree> =
            hotRoutesRepository.routes
                .map { routes ->
                    HotItemTree(
                        allIds = routes.map { RouteRepository.idForRoute(it.name, it.city) }.toSet(),
                        byCountry =
                            routes.groupBy { it.country }.mapValues { (_, rs) ->
                                rs.groupBy { it.city }.mapValues { (_, items) ->
                                    items.map { HotItemEntry(RouteRepository.idForRoute(it.name, it.city), it.name) }
                                }
                            },
                    )
                }.stateIn(viewModelScope, SharingStarted.Eagerly, HotItemTree.Empty)

        /** Clears all favorites, routes, and settings. Preserves onboarding-completion state. */
        fun resetAllData() {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        favoriteRepository.deleteAllFavorites()
                        routeRepository.deleteAllRoutes()
                    }
                    settingsRepository.resetAllData()
                    emitFeedback(R.string.settings_viewmodel_all_data_reset)
                } catch (e: Exception) {
                    Log.e(TAG, "Reset all data failed", e)
                    emitFeedback(R.string.settings_viewmodel_failed_to_reset_data, isError = true)
                }
            }
        }

        private fun convertDisplayToMs(
            displaySpeed: Double,
            unit: SpeedUnit,
        ): Double =
            when (unit) {
                SpeedUnit.KMH -> displaySpeed / 3.6
                SpeedUnit.MPH -> displaySpeed / 2.237
            }

        /** Maps [SettingsSnapshot]'s fixed per-profile fields to a keyed map for [SettingsUiState.speeds]. */
        private fun snapshotSpeeds(snapshot: SettingsSnapshot): Map<String, Double> =
            mapOf(
                "slow_walk" to snapshot.slowWalkSpeedMs,
                "walk" to snapshot.walkSpeedMs,
                "run" to snapshot.runSpeedMs,
                "bike" to snapshot.bikeSpeedMs,
                "drive" to snapshot.driveSpeedMs,
            )

        private suspend fun buildCurrentExportData(): ExportData {
            val state = uiState.value
            val speedProfiles =
                SpeedProfile.defaultProfiles().map { it.copy(speedMetersPerSecond = state.speeds.getValue(it.id)) }
            return ExportData(
                schemaVersion = AppConstants.ExportConstants.SCHEMA_VERSION,
                exportedAt = System.currentTimeMillis(),
                settings = state.toAppSettings(),
                speedProfiles = speedProfiles,
                routes = routeRepository.getRoutes().first(),
                favoriteLocations = favoriteRepository.getFavorites().first(),
                jitterIdleRadius = state.jitterIdleRadiusMeters,
                jitterMovingRadius = state.jitterMovingRadiusMeters,
                jitterMaxStepMeters = state.jitterMaxStepMeters,
                jitterSpeedIdleVariationPct = state.jitterSpeedIdleVariationPct,
                jitterSpeedMovingVariationPct = state.jitterSpeedMovingVariationPct,
                jitterSpeedIdleWobbleProbabilityPct = state.jitterSpeedIdleWobbleProbabilityPct,
                hotLocationsEnabled = state.hotLocationsEnabled,
                selectedHotLocationIds = state.selectedHotLocationIds,
                hotRoutesEnabled = state.hotRoutesEnabled,
                selectedHotRouteIds = state.selectedHotRouteIds,
                routesSortNewestFirst = settingsRepository.getRoutesSortNewestFirst().first(),
                favoritesSortNewestFirst = settingsRepository.getFavoritesSortNewestFirst().first(),
                homeFavoriteId = settingsRepository.getHomeFavoriteId().first(),
                routesSortMode = settingsRepository.getRoutesSortMode().first(),
                favoritesSortMode = settingsRepository.getFavoritesSortMode().first(),
                favoritesManualOrder = settingsRepository.getFavoritesManualOrder().first(),
                routesManualOrder = settingsRepository.getRoutesManualOrder().first(),
            )
        }

        fun writeExportToUri(uri: Uri) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val json = SettingsExportCodec.serializeExportData(buildCurrentExportData())
                    importExportRepository.writeToUri(uri, json)
                    emitFeedback(R.string.settings_viewmodel_export_complete)
                } catch (e: Exception) {
                    Log.e(TAG, "Export failed", e)
                    emitFeedback(R.string.settings_viewmodel_failed_to_export, isError = true)
                }
            }
        }

        fun importSettings(
            uri: Uri,
            replace: Boolean = true,
        ) {
            viewModelScope.launch {
                try {
                    val json = withContext(Dispatchers.IO) { importExportRepository.readTextFromUri(uri) }
                    if (json.isEmpty()) {
                        Log.e(TAG, "Import failed: empty file")
                        emitFeedback(R.string.settings_viewmodel_failed_to_import_empty_file, isError = true)
                        return@launch
                    }
                    applyExportData(SettingsExportCodec.parseExportData(json), replace)
                    emitImportComplete()
                } catch (e: Exception) {
                    Log.e(TAG, "Import failed", e)
                    emitFeedback(R.string.settings_viewmodel_failed_to_import, isError = true)
                }
            }
        }

        private val exportShare = ExportShareSession(exportSyncServer, nsdCodeManager, viewModelScope)

        /**
         * Starts [exportSyncServer] (HTTP) and [nsdCodeManager] (NSD advertising), and emits a QR
         * code + typeable code for the same session. Call [stopQrExport] when the share dialog closes.
         */
        fun prepareQrExport() {
            _isPreparingQrExport.value = true
            exportShare.start { serve ->
                try {
                    val json = SettingsExportCodec.serializeExportData(buildCurrentExportData())
                    val host =
                        NetworkUtils.getLocalIpAddress()
                            ?: error("Cannot determine local IP — ensure Wi-Fi is connected")
                    val code = RandomCode.generate()
                    val port = serve(code, json)
                    qrExportReady.emit(
                        QrExportSession(qrText = "locationjoystick://export?host=$host&port=$port&token=$code", code = code),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "QR export preparation failed", e)
                    emitFeedback(R.string.settings_viewmodel_failed_to_prepare_qr_export, isError = true)
                } finally {
                    _isPreparingQrExport.value = false
                }
            }
        }

        fun stopQrExport() = exportShare.stop()

        fun onQrScanned(url: String) {
            val uri = Uri.parse(url)
            val host = uri.getQueryParameter("host")
            val port = uri.getQueryParameter("port")?.toIntOrNull()
            val token = uri.getQueryParameter("token")
            viewModelScope.launch {
                if (host == null || port == null || token == null) {
                    Log.e(TAG, "Unrecognized QR code: $url")
                    emitFeedback(R.string.settings_viewmodel_invalid_qr_code, isError = true)
                    return@launch
                }
                fetchAndImportExport(host, port, token)
            }
        }

        /** Alternative to scanning a QR — resolve the sender via NSD using the typed code instead. */
        fun onExportCodeEntered(code: String) {
            val normalized = code.uppercase().trim()
            viewModelScope.launch {
                if (normalized.length != AppConstants.SyncConstants.GROUP_CODE_LENGTH) {
                    emitQuantityFeedback(
                        R.plurals.settings_viewmodel_code_must_be_n_characters,
                        AppConstants.SyncConstants.GROUP_CODE_LENGTH,
                        isError = true,
                    )
                    return@launch
                }
                val resolved = nsdCodeManager.discoverByCode(normalized)
                if (resolved == null) {
                    emitFeedback(R.string.settings_viewmodel_no_sender_found_for_code, normalized, isError = true)
                    return@launch
                }
                val (host, port) = resolved
                fetchAndImportExport(host, port, normalized)
            }
        }

        /** Sole owner of [_qrImportFetching] for the duration of the fetch. */
        private suspend fun fetchAndImportExport(
            host: String,
            port: Int,
            token: String,
        ) {
            _qrImportFetching.value = true
            try {
                val json = exportSyncClient.fetch(host, port, token)
                val data = withContext(Dispatchers.Default) { SettingsExportCodec.parseExportData(json) }
                _qrImportReady.emit(data)
            } catch (e: Exception) {
                Log.e(TAG, "QR import fetch failed", e)
                emitFeedback(R.string.settings_viewmodel_failed_to_fetch_export, isError = true)
            } finally {
                _qrImportFetching.value = false
            }
        }

        fun importSettings(
            exportData: ExportData,
            replace: Boolean = true,
        ) {
            viewModelScope.launch {
                try {
                    applyExportData(exportData, replace)
                    emitImportComplete()
                } catch (e: Exception) {
                    Log.e(TAG, "Import from ExportData failed", e)
                    emitFeedback(R.string.settings_viewmodel_failed_to_import, isError = true)
                }
            }
        }

        private suspend fun emitImportComplete() {
            val accepted =
                settingsRepository.getAccessibilityDisclosureChoice().first() == AppConstants.AccessibilityConstants.DISCLOSURE_ACCEPTED
            val needsConsent = showsReconnectConsentPrompt(settingsRepository.getReconnectTapEnabled().first(), accepted)
            val message =
                if (needsConsent) {
                    R.string.settings_viewmodel_import_complete_reconnect_consent
                } else {
                    R.string.settings_viewmodel_import_complete
                }
            emitFeedback(message)
        }

        private suspend fun applyExportData(
            data: ExportData,
            replace: Boolean,
        ) {
            backupRepository.apply(data.favoriteLocations, data.routes, clearFavorites = replace, clearRoutes = replace)
            // Preserve fields not present in ExportData by reading current persisted values.
            val currentSnapshot = settingsRepository.getSettingsSnapshot().first()
            val profileById = data.speedProfiles.associateBy { it.id }
            settingsRepository.applySnapshot(
                currentSnapshot.copy(
                    slowWalkSpeedMs = profileById["slow_walk"]?.speedMetersPerSecond ?: currentSnapshot.slowWalkSpeedMs,
                    walkSpeedMs = profileById["walk"]?.speedMetersPerSecond ?: currentSnapshot.walkSpeedMs,
                    runSpeedMs = profileById["run"]?.speedMetersPerSecond ?: currentSnapshot.runSpeedMs,
                    bikeSpeedMs = profileById["bike"]?.speedMetersPerSecond ?: currentSnapshot.bikeSpeedMs,
                    driveSpeedMs = profileById["drive"]?.speedMetersPerSecond ?: currentSnapshot.driveSpeedMs,
                    speedUnit = data.settings.speedUnit,
                    mapTileSource = data.settings.mapTileSource,
                    guidedPath = data.settings.guidedPath,
                    disabledGeocodingProviders = data.settings.disabledGeocodingProviders,
                    featureOrder = data.settings.featureOrder,
                    enabledWidgetFeatures = data.settings.enabledWidgetFeatures,
                    enabledMapFeatures = data.settings.enabledMapFeatures,
                    enabledSpeedProfileIds = data.settings.enabledSpeedProfileIds,
                    jitterIdleRadius = data.jitterIdleRadius,
                    jitterMovingRadius = data.jitterMovingRadius,
                    jitterMaxStepMeters = data.jitterMaxStepMeters,
                    realismBearingHoldIdle = data.settings.bearingHoldOnIdle,
                    realismAltitudeEnabled = data.settings.altitudeEnabled,
                    realismWarmupEnabled = data.settings.warmupEnabled,
                    realismSatelliteExtrasEnabled = data.settings.satelliteExtrasEnabled,
                    realismSuspendedMockingEnabled = data.settings.suspendedMockingEnabled,
                    hideTeleportFeatures = data.settings.hideTeleportFeatures,
                    hideWidgetOverlay = data.settings.hideWidgetOverlay,
                    hideForegroundNotification = data.settings.hideForegroundNotification,
                    showRouteJumpButtons = data.settings.showRouteJumpButtons,
                    lastKnownPositionEnabled = data.settings.lastKnownPositionEnabled,
                    bypassMockLocationCheck = data.settings.bypassMockLocationCheck,
                    realismRealElevationEnabled = data.settings.realElevationEnabled,
                    altitudeJitterRadiusMeters = data.settings.altitudeJitterRadiusMeters,
                    plantingRadiusMeters = data.settings.plantingRadiusMeters,
                    altitudeOverrideButtonEnabled = data.settings.altitudeOverrideButtonEnabled,
                    debugStatsEnabled = data.settings.debugStatsEnabled,
                    jitterSpeedIdleVariationPct = data.jitterSpeedIdleVariationPct,
                    jitterSpeedMovingVariationPct = data.jitterSpeedMovingVariationPct,
                    jitterSpeedIdleWobbleProbabilityPct = data.jitterSpeedIdleWobbleProbabilityPct,
                    hotLocationsEnabled = data.hotLocationsEnabled,
                    selectedHotLocationIds = data.selectedHotLocationIds,
                    hotRoutesEnabled = data.hotRoutesEnabled,
                    selectedHotRouteIds = data.selectedHotRouteIds,
                    roamingDefaults = data.settings.roamingDefaults,
                    rememberLastLocation = data.settings.rememberLastLocation,
                    mapFollowsLocation = data.settings.mapFollowsLocation,
                    floatingMapQuickWalk = data.settings.floatingMapQuickWalk,
                ),
            )
            settingsRepository.setTapToWalkOverlayEnabled(data.settings.tapToWalkOverlayEnabled)
            settingsRepository.setActiveProfileId(data.settings.activeSpeedProfileId)
            settingsRepository.setReconnectTapEnabled(data.settings.reconnectTapEnabled)
            settingsRepository.setKeepWidgetOnIdle(data.settings.keepWidgetOnIdle)
            settingsRepository.setThemeMode(data.settings.themeMode)
            if (data.hotLocationsEnabled) {
                favoriteRepository.upsertHotLocations(hotLocationsRepository.locations.value, data.selectedHotLocationIds)
            } else {
                favoriteRepository.removeHotLocations()
            }
            if (data.hotRoutesEnabled) {
                routeRepository.upsertHotRoutes(hotRoutesRepository.routes.value, data.selectedHotRouteIds)
            } else {
                routeRepository.removeHotRoutes()
            }
            if (replace) {
                settingsRepository.setRoutesSortNewestFirst(data.routesSortNewestFirst)
                settingsRepository.setFavoritesSortNewestFirst(data.favoritesSortNewestFirst)
                settingsRepository.setRoutesSortMode(data.routesSortMode)
                settingsRepository.setFavoritesSortMode(data.favoritesSortMode)
                // After the sort modes: choosing a mode clears the manual order.
                if (data.favoritesManualOrder.isNotEmpty()) settingsRepository.setFavoritesManualOrder(data.favoritesManualOrder)
                if (data.routesManualOrder.isNotEmpty()) settingsRepository.setRoutesManualOrder(data.routesManualOrder)
            } else {
                val favoritesOrder = settingsRepository.getFavoritesManualOrder().first()
                if (favoritesOrder.isNotEmpty()) {
                    val imported = data.favoritesManualOrder + data.favoriteLocations.map { it.id }
                    settingsRepository.setFavoritesManualOrder((favoritesOrder + imported).distinct())
                }
                val routesOrder = settingsRepository.getRoutesManualOrder().first()
                if (routesOrder.isNotEmpty()) {
                    val imported = data.routesManualOrder + data.routes.map { it.id }
                    settingsRepository.setRoutesManualOrder((routesOrder + imported).distinct())
                }
            }
            if (replace || data.homeFavoriteId != null) settingsRepository.setHomeFavoriteId(data.homeFavoriteId)
            clearHomeIfMissing()
        }

        /** Home is a favorite id; clear it when that favorite no longer exists (e.g. hot locations turned off). */
        private suspend fun clearHomeIfMissing() {
            val id = settingsRepository.getHomeFavoriteId().first() ?: return
            if (favoriteRepository.getFavorites().first().none { it.id == id }) settingsRepository.setHomeFavoriteId(null)
        }

        fun importFromGpsJoystick(
            uri: Uri,
            replace: Boolean,
        ) {
            viewModelScope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) { importExportRepository.readBytesFromUri(uri) }
                    if (bytes.isEmpty()) {
                        Log.e(TAG, "GPS Joystick import failed: empty file")
                        emitFeedback(R.string.settings_viewmodel_failed_to_import_from_gps_joystick, isError = true)
                        return@launch
                    }
                    val result = GpsJoystickMigrator.parse(bytes)
                    if (result.isFailure) {
                        Log.e(TAG, "GPS Joystick import failed: ${result.exceptionOrNull()?.message}")
                        val hint = result.exceptionOrNull()?.message
                        userFeedback.emit(
                            UserFeedback(
                                if (hint != null && hint.contains("GPX")) {
                                    hint
                                } else {
                                    context.getString(R.string.settings_viewmodel_failed_to_import_from_gps_joystick)
                                },
                                isError = true,
                            ),
                        )
                        return@launch
                    }
                    val migration = result.getOrNull() ?: return@launch
                    backupRepository.apply(migration.favorites, migration.routes, clearFavorites = replace, clearRoutes = replace)
                    Log.i(
                        TAG,
                        "GPS Joystick import complete: ${migration.favorites.size} favorites, " +
                            "${migration.routes.size} routes, skipped ${migration.skippedOversizedRouteCount}",
                    )
                    userFeedback.emit(UserFeedback(migration.toImportMessage(context)))
                } catch (e: Exception) {
                    Log.e(TAG, "GPS Joystick import failed", e)
                    emitFeedback(R.string.settings_viewmodel_failed_to_import_from_gps_joystick, isError = true)
                }
            }
        }

        fun importFromYamla(
            uri: Uri,
            replace: Boolean,
        ) {
            viewModelScope.launch {
                try {
                    val json = withContext(Dispatchers.IO) { importExportRepository.readTextFromUri(uri) }
                    if (json.isBlank()) {
                        Log.e(TAG, "YAMLA import failed: empty file")
                        emitFeedback(R.string.settings_viewmodel_failed_to_import_from_yamla, isError = true)
                        return@launch
                    }
                    val result = YamlaMigrator.parse(json)
                    if (result.isFailure) {
                        Log.e(TAG, "YAMLA import failed: ${result.exceptionOrNull()?.message}")
                        emitFeedback(R.string.settings_viewmodel_failed_to_import_from_yamla, isError = true)
                        return@launch
                    }
                    val migration = result.getOrNull() ?: return@launch
                    backupRepository.apply(migration.favorites, emptyList(), clearFavorites = replace, clearRoutes = false)
                    migration.walkSpeed?.let { settingsRepository.setWalkSpeed(it) }
                    migration.runSpeed?.let { settingsRepository.setRunSpeed(it) }
                    migration.bikeSpeed?.let { settingsRepository.setBikeSpeed(it) }
                    val speedsUpdated = migration.walkSpeed != null
                    Log.i(
                        TAG,
                        "YAMLA import complete: ${migration.favorites.size} favorites" +
                            if (speedsUpdated) ", speeds updated" else "",
                    )
                    emitQuantityFeedback(
                        if (speedsUpdated) {
                            R.plurals.settings_viewmodel_imported_favorites_from_yamla_speeds_updated
                        } else {
                            R.plurals.settings_viewmodel_imported_favorites_from_yamla
                        },
                        migration.favorites.size,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "YAMLA import failed", e)
                    emitFeedback(R.string.settings_viewmodel_failed_to_import_from_yamla, isError = true)
                }
            }
        }
    }
