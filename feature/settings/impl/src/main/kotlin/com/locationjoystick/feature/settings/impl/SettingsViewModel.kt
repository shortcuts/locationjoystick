package com.locationjoystick.feature.settings.impl

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.root.SensorPermissionBootstrap
import com.locationjoystick.core.common.util.NetworkUtils
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.common.util.RandomCode
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.datastore.SettingsSnapshot
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.AppSettings
import com.locationjoystick.core.model.ExportData
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val favoriteRepository: FavoriteRepository,
        private val routeRepository: RouteRepository,
        private val sensorPermissionBootstrap: SensorPermissionBootstrap,
        private val importExportRepository: ImportExportRepository,
        private val exportSyncServer: ExportSyncServer,
        private val exportSyncClient: ExportSyncClient,
        private val nsdCodeManager: NsdCodeManager,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        companion object {
            private const val TAG = "SettingsViewModel"

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

        private val compassServiceGranted = MutableStateFlow(false)

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

        /** [qrText] renders as the QR; [code] is the same session typeable manually on the other device. */
        internal data class QrExportSession(
            val qrText: String,
            val code: String,
        )

        internal val qrExportReady = MutableSharedFlow<QrExportSession>(extraBufferCapacity = 1)

        internal val userFeedback = MutableSharedFlow<UserFeedback>(extraBufferCapacity = 1)

        private data class DraftState(
            val walkSpeed: Double? = null,
            val runSpeed: Double? = null,
            val bikeSpeed: Double? = null,
            val speedUnit: SpeedUnit? = null,
            val featureOrder: List<AppFeature>? = null,
            val widgetFeatures: Set<AppFeature>? = null,
            val mapFeatures: Set<AppFeature>? = null,
            val rememberLastLocation: Boolean? = null,
            val mapFollowsLocation: Boolean? = null,
            val jitterIdleRadius: Double? = null,
            val jitterMovingRadius: Double? = null,
            val jitterIntervalSeconds: Int? = null,
            val jitterIdleIntervalSeconds: Int? = null,
            val roamingDefaults: RoamingDefaults? = null,
            val realismBearingHoldIdle: Boolean? = null,
            val realismAltitudeEnabled: Boolean? = null,
            val realismWarmupEnabled: Boolean? = null,
            val realismSatelliteExtrasEnabled: Boolean? = null,
            val realismSuspendedMockingEnabled: Boolean? = null,
            val realismPedometerMockingEnabled: Boolean? = null,
            val jitterSpeedIdleVariationPct: Int? = null,
            val jitterSpeedMovingVariationPct: Int? = null,
            val hotLocationsEnabled: Boolean? = null,
            val selectedHotLocationIds: Set<String>? = null,
            val hotRoutesEnabled: Boolean? = null,
            val selectedHotRouteIds: Set<String>? = null,
            val floatingMapQuickWalk: Boolean? = null,
            val tapToWalkOverlayEnabled: Boolean? = null,
            val tapToWalkScaleMpx: Double? = null,
        )

        private val mutableDraft = MutableStateFlow(DraftState())

        private val snapshotFlow = settingsRepository.getSettingsSnapshot()

        private val draftStateFlow = mutableDraft.asStateFlow()

        val roamingDefaults: StateFlow<RoamingDefaults> =
            combine(
                settingsRepository.getRoamingDefaults(),
                mutableDraft,
            ) { repo, draft -> draft.roamingDefaults ?: repo }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = RoamingDefaults(),
                )

        private data class CompassPrefsState(
            val enabled: Boolean = false,
            val cx: Float = AppConstants.CompassTrackingConstants.DEFAULT_REGION_CX_PCT,
            val cy: Float = AppConstants.CompassTrackingConstants.DEFAULT_REGION_CY_PCT,
            val radius: Float = AppConstants.CompassTrackingConstants.DEFAULT_REGION_RADIUS_PCT,
        )

        private val compassPrefsFlow =
            combine(
                settingsRepository.getCompassTrackingEnabled(),
                settingsRepository.getCompassRegionCxPct(),
                settingsRepository.getCompassRegionCyPct(),
                settingsRepository.getCompassRegionRadiusPct(),
            ) { enabled, cx, cy, radius -> CompassPrefsState(enabled, cx, cy, radius) }

        val uiState: StateFlow<SettingsUiState> =
            combine(
                combine(snapshotFlow, draftStateFlow) { snapshot, draft -> Pair(snapshot, draft) },
                compassPrefsFlow,
                compassServiceGranted,
            ) { (snapshot, draftState), compass, isServiceGranted ->
                val isDirty = draftState != DraftState()
                SettingsUiState(
                    isLoading = false,
                    walkSpeed = draftState.walkSpeed ?: snapshot.walkSpeedMs,
                    runSpeed = draftState.runSpeed ?: snapshot.runSpeedMs,
                    bikeSpeed = draftState.bikeSpeed ?: snapshot.bikeSpeedMs,
                    speedUnit = draftState.speedUnit ?: snapshot.speedUnit,
                    featureOrder = draftState.featureOrder ?: snapshot.featureOrder,
                    enabledWidgetFeatures = draftState.widgetFeatures ?: snapshot.enabledWidgetFeatures,
                    enabledMapFeatures = draftState.mapFeatures ?: snapshot.enabledMapFeatures,
                    rememberLastLocation = draftState.rememberLastLocation ?: snapshot.rememberLastLocation,
                    mapFollowsLocation = draftState.mapFollowsLocation ?: snapshot.mapFollowsLocation,
                    jitterIdleRadiusMeters = draftState.jitterIdleRadius ?: snapshot.jitterIdleRadius,
                    jitterMovingRadiusMeters = draftState.jitterMovingRadius ?: snapshot.jitterMovingRadius,
                    jitterIntervalSeconds = draftState.jitterIntervalSeconds ?: snapshot.jitterIntervalSeconds,
                    jitterIdleIntervalSeconds = draftState.jitterIdleIntervalSeconds ?: snapshot.jitterIdleIntervalSeconds,
                    realismBearingHoldIdle = draftState.realismBearingHoldIdle ?: snapshot.realismBearingHoldIdle,
                    realismAltitudeEnabled = draftState.realismAltitudeEnabled ?: snapshot.realismAltitudeEnabled,
                    realismWarmupEnabled = draftState.realismWarmupEnabled ?: snapshot.realismWarmupEnabled,
                    realismSatelliteExtrasEnabled = draftState.realismSatelliteExtrasEnabled ?: snapshot.realismSatelliteExtrasEnabled,
                    realismSuspendedMockingEnabled = draftState.realismSuspendedMockingEnabled ?: snapshot.realismSuspendedMockingEnabled,
                    realismPedometerMockingEnabled = draftState.realismPedometerMockingEnabled ?: snapshot.realismPedometerMockingEnabled,
                    jitterSpeedIdleVariationPct = draftState.jitterSpeedIdleVariationPct ?: snapshot.jitterSpeedIdleVariationPct,
                    jitterSpeedMovingVariationPct = draftState.jitterSpeedMovingVariationPct ?: snapshot.jitterSpeedMovingVariationPct,
                    hotLocationsEnabled = draftState.hotLocationsEnabled ?: snapshot.hotLocationsEnabled,
                    selectedHotLocationIds = draftState.selectedHotLocationIds ?: snapshot.selectedHotLocationIds,
                    hotRoutesEnabled = draftState.hotRoutesEnabled ?: snapshot.hotRoutesEnabled,
                    selectedHotRouteIds = draftState.selectedHotRouteIds ?: snapshot.selectedHotRouteIds,
                    floatingMapQuickWalk = draftState.floatingMapQuickWalk ?: snapshot.floatingMapQuickWalk,
                    tapToWalkOverlayEnabled = draftState.tapToWalkOverlayEnabled ?: snapshot.tapToWalkOverlayEnabled,
                    tapToWalkScaleMpx = draftState.tapToWalkScaleMpx ?: snapshot.tapToWalkScaleMpx,
                    compassTrackingEnabled = compass.enabled,
                    isCompassServiceGranted = isServiceGranted,
                    compassRegionCxPct = compass.cx,
                    compassRegionCyPct = compass.cy,
                    compassRegionRadiusPct = compass.radius,
                    isDirty = isDirty,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(isLoading = true),
            )

        fun setWalkSpeed(displaySpeed: Double) {
            mutableDraft.update { it.copy(walkSpeed = convertDisplayToMs(displaySpeed, uiState.value.speedUnit)) }
        }

        fun setRunSpeed(displaySpeed: Double) {
            mutableDraft.update { it.copy(runSpeed = convertDisplayToMs(displaySpeed, uiState.value.speedUnit)) }
        }

        fun setBikeSpeed(displaySpeed: Double) {
            mutableDraft.update { it.copy(bikeSpeed = convertDisplayToMs(displaySpeed, uiState.value.speedUnit)) }
        }

        fun setSpeedUnit(unit: SpeedUnit) {
            mutableDraft.update { it.copy(speedUnit = unit) }
        }

        fun setWidgetFeatures(features: Set<AppFeature>) {
            mutableDraft.update { it.copy(widgetFeatures = features) }
        }

        fun setMapFeatures(features: Set<AppFeature>) {
            mutableDraft.update { it.copy(mapFeatures = features) }
        }

        fun setFeatureOrder(order: List<AppFeature>) {
            mutableDraft.update { it.copy(featureOrder = order) }
        }

        fun setRememberLastLocation(enabled: Boolean) {
            mutableDraft.update { it.copy(rememberLastLocation = enabled) }
        }

        fun setMapFollowsLocation(enabled: Boolean) {
            mutableDraft.update { it.copy(mapFollowsLocation = enabled) }
        }

        fun setJitterIdleRadius(meters: Double) {
            mutableDraft.update { it.copy(jitterIdleRadius = meters) }
        }

        fun setJitterMovingRadius(meters: Double) {
            mutableDraft.update { it.copy(jitterMovingRadius = meters) }
        }

        fun setJitterIntervalSeconds(seconds: Int) {
            mutableDraft.update { it.copy(jitterIntervalSeconds = seconds) }
        }

        fun setJitterIdleIntervalSeconds(seconds: Int) {
            mutableDraft.update { it.copy(jitterIdleIntervalSeconds = seconds) }
        }

        fun updateRoamingDefaults(defaults: RoamingDefaults) {
            mutableDraft.update { it.copy(roamingDefaults = defaults) }
        }

        fun setRealismBearingHoldIdle(v: Boolean) {
            mutableDraft.update { it.copy(realismBearingHoldIdle = v) }
        }

        fun setRealismAltitudeEnabled(v: Boolean) {
            mutableDraft.update { it.copy(realismAltitudeEnabled = v) }
        }

        fun setRealismWarmupEnabled(v: Boolean) {
            mutableDraft.update { it.copy(realismWarmupEnabled = v) }
        }

        fun setRealismSatelliteExtrasEnabled(v: Boolean) {
            mutableDraft.update { it.copy(realismSatelliteExtrasEnabled = v) }
        }

        fun setRealismSuspendedMockingEnabled(v: Boolean) {
            mutableDraft.update { it.copy(realismSuspendedMockingEnabled = v) }
        }

        fun setRealismPedometerMockingEnabled(v: Boolean) {
            mutableDraft.update { it.copy(realismPedometerMockingEnabled = v) }
        }

        fun setJitterSpeedIdleVariationPct(pct: Int) {
            mutableDraft.update { it.copy(jitterSpeedIdleVariationPct = pct) }
        }

        fun setJitterSpeedMovingVariationPct(pct: Int) {
            mutableDraft.update { it.copy(jitterSpeedMovingVariationPct = pct) }
        }

        fun setHotLocationsEnabled(enabled: Boolean) {
            val allIds = FavoriteRepository.HOT_LOCATIONS.map { FavoriteRepository.idForLocation(it.name, it.city) }.toSet()
            mutableDraft.update { draft ->
                val currentSelectedIds = draft.selectedHotLocationIds ?: uiState.value.selectedHotLocationIds
                val newSelectedIds = if (enabled && currentSelectedIds.isEmpty()) allIds else draft.selectedHotLocationIds
                draft.copy(hotLocationsEnabled = enabled, selectedHotLocationIds = newSelectedIds)
            }
        }

        fun setSelectedHotLocationIds(ids: Set<String>) {
            mutableDraft.update { it.copy(selectedHotLocationIds = ids) }
        }

        val hotLocationTree: HotItemTree =
            run {
                val locations = FavoriteRepository.HOT_LOCATIONS
                HotItemTree(
                    allIds = locations.map { FavoriteRepository.idForLocation(it.name, it.city) }.toSet(),
                    byCountry =
                        locations.groupBy { it.country }.mapValues { (_, locs) ->
                            locs.groupBy { it.city }.mapValues { (_, items) ->
                                items.map { HotItemEntry(FavoriteRepository.idForLocation(it.name, it.city), it.name) }
                            }
                        },
                )
            }

        fun setHotRoutesEnabled(enabled: Boolean) {
            val allIds = RouteRepository.HOT_ROUTES.map { RouteRepository.idForRoute(it.name, it.city) }.toSet()
            mutableDraft.update { draft ->
                val currentSelectedIds = draft.selectedHotRouteIds ?: uiState.value.selectedHotRouteIds
                val newSelectedIds = if (enabled && currentSelectedIds.isEmpty()) allIds else draft.selectedHotRouteIds
                draft.copy(hotRoutesEnabled = enabled, selectedHotRouteIds = newSelectedIds)
            }
        }

        fun setSelectedHotRouteIds(ids: Set<String>) {
            mutableDraft.update { it.copy(selectedHotRouteIds = ids) }
        }

        fun setFloatingMapQuickWalk(enabled: Boolean) {
            mutableDraft.update { it.copy(floatingMapQuickWalk = enabled) }
        }

        fun setTapToWalkOverlayEnabled(enabled: Boolean) {
            mutableDraft.update { it.copy(tapToWalkOverlayEnabled = enabled) }
        }

        fun setTapToWalkScaleMpx(scale: Double) {
            mutableDraft.update { it.copy(tapToWalkScaleMpx = scale) }
        }

        fun setCompassTrackingEnabled(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setCompassTrackingEnabled(enabled) }
        }

        fun setCompassRegion(
            cx: Float,
            cy: Float,
            radius: Float,
        ) {
            viewModelScope.launch {
                settingsRepository.setCompassRegionCxPct(cx)
                settingsRepository.setCompassRegionCyPct(cy)
                settingsRepository.setCompassRegionRadiusPct(radius)
            }
        }

        fun checkCompassServiceGranted() {
            val am = context.getSystemService(AccessibilityManager::class.java)
            compassServiceGranted.value =
                am
                    ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    ?.any { it.id.contains("CompassAccessibilityService") }
                    ?: false
        }

        val hotRouteTree: HotItemTree =
            run {
                val routes = RouteRepository.HOT_ROUTES
                HotItemTree(
                    allIds = routes.map { RouteRepository.idForRoute(it.name, it.city) }.toSet(),
                    byCountry =
                        routes.groupBy { it.country }.mapValues { (_, rs) ->
                            rs.groupBy { it.city }.mapValues { (_, items) ->
                                items.map { HotItemEntry(RouteRepository.idForRoute(it.name, it.city), it.name) }
                            }
                        },
                )
            }

        fun saveChanges() {
            viewModelScope.launch {
                try {
                    val state = uiState.value
                    val d = mutableDraft.value
                    settingsRepository.applySnapshot(
                        SettingsSnapshot(
                            walkSpeedMs = state.walkSpeed,
                            runSpeedMs = state.runSpeed,
                            bikeSpeedMs = state.bikeSpeed,
                            speedUnit = state.speedUnit,
                            featureOrder = state.featureOrder,
                            enabledWidgetFeatures = state.enabledWidgetFeatures,
                            enabledMapFeatures = state.enabledMapFeatures,
                            rememberLastLocation = state.rememberLastLocation,
                            mapFollowsLocation = state.mapFollowsLocation,
                            jitterIdleRadius = state.jitterIdleRadiusMeters,
                            jitterMovingRadius = state.jitterMovingRadiusMeters,
                            jitterIntervalSeconds = state.jitterIntervalSeconds,
                            jitterIdleIntervalSeconds = state.jitterIdleIntervalSeconds,
                            realismBearingHoldIdle = state.realismBearingHoldIdle,
                            realismAltitudeEnabled = state.realismAltitudeEnabled,
                            realismWarmupEnabled = state.realismWarmupEnabled,
                            realismSatelliteExtrasEnabled = state.realismSatelliteExtrasEnabled,
                            realismSuspendedMockingEnabled = state.realismSuspendedMockingEnabled,
                            realismPedometerMockingEnabled = state.realismPedometerMockingEnabled,
                            jitterSpeedIdleVariationPct = state.jitterSpeedIdleVariationPct,
                            jitterSpeedMovingVariationPct = state.jitterSpeedMovingVariationPct,
                            hotLocationsEnabled = state.hotLocationsEnabled,
                            selectedHotLocationIds = state.selectedHotLocationIds,
                            hotRoutesEnabled = state.hotRoutesEnabled,
                            selectedHotRouteIds = state.selectedHotRouteIds,
                            floatingMapQuickWalk = state.floatingMapQuickWalk,
                            tapToWalkOverlayEnabled = state.tapToWalkOverlayEnabled,
                            tapToWalkScaleMpx = state.tapToWalkScaleMpx,
                            roamingDefaults =
                                d.roamingDefaults
                                    ?: settingsRepository.getRoamingDefaults().first(),
                        ),
                    )
                    if (d.hotLocationsEnabled != null || d.selectedHotLocationIds != null) {
                        if (state.hotLocationsEnabled) {
                            favoriteRepository.upsertHotLocations(state.selectedHotLocationIds)
                        } else {
                            favoriteRepository.removeHotLocations()
                        }
                    }
                    if (d.hotRoutesEnabled != null || d.selectedHotRouteIds != null) {
                        if (state.hotRoutesEnabled) {
                            routeRepository.upsertHotRoutes(state.selectedHotRouteIds)
                        } else {
                            routeRepository.removeHotRoutes()
                        }
                    }
                    mutableDraft.value = DraftState()
                    userFeedback.emit(UserFeedback("Settings saved"))
                } catch (e: Exception) {
                    Log.e(TAG, "Save failed", e)
                    userFeedback.emit(UserFeedback("Failed to save settings", isError = true))
                }
            }
        }

        fun discardChanges() {
            mutableDraft.value = DraftState()
        }

        private fun convertDisplayToMs(
            displaySpeed: Double,
            unit: SpeedUnit,
        ): Double =
            when (unit) {
                SpeedUnit.KMH -> displaySpeed / 3.6
                SpeedUnit.MPH -> displaySpeed / 2.237
            }

        private suspend fun buildCurrentExportData(): ExportData {
            val state = uiState.value
            val speedProfiles =
                listOf(
                    SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = state.walkSpeed),
                    SpeedProfile(id = "run", name = "Run", speedMetersPerSecond = state.runSpeed),
                    SpeedProfile(id = "bike", name = "Bike", speedMetersPerSecond = state.bikeSpeed),
                )
            val settings =
                AppSettings(
                    speedUnit = state.speedUnit,
                    featureOrder = state.featureOrder,
                    enabledWidgetFeatures = state.enabledWidgetFeatures,
                    enabledMapFeatures = state.enabledMapFeatures,
                    bearingHoldOnIdle = state.realismBearingHoldIdle,
                    altitudeEnabled = state.realismAltitudeEnabled,
                    warmupEnabled = state.realismWarmupEnabled,
                    satelliteExtrasEnabled = state.realismSatelliteExtrasEnabled,
                    suspendedMockingEnabled = state.realismSuspendedMockingEnabled,
                    pedometerMockingEnabled = state.realismPedometerMockingEnabled,
                )
            return ExportData(
                schemaVersion = AppConstants.ExportConstants.SCHEMA_VERSION,
                exportedAt = System.currentTimeMillis(),
                settings = settings,
                speedProfiles = speedProfiles,
                routes = routeRepository.getRoutes().first(),
                favoriteLocations = favoriteRepository.getFavorites().first(),
                jitterIdleRadius = state.jitterIdleRadiusMeters,
                jitterMovingRadius = state.jitterMovingRadiusMeters,
                jitterIntervalSeconds = state.jitterIntervalSeconds,
                jitterIdleIntervalSeconds = state.jitterIdleIntervalSeconds,
                jitterSpeedIdleVariationPct = state.jitterSpeedIdleVariationPct,
                jitterSpeedMovingVariationPct = state.jitterSpeedMovingVariationPct,
                hotLocationsEnabled = state.hotLocationsEnabled,
                selectedHotLocationIds = state.selectedHotLocationIds,
                hotRoutesEnabled = state.hotRoutesEnabled,
                selectedHotRouteIds = state.selectedHotRouteIds,
                routesSortNewestFirst = settingsRepository.getRoutesSortNewestFirst().first(),
                favoritesSortNewestFirst = settingsRepository.getFavoritesSortNewestFirst().first(),
            )
        }

        fun writeExportToUri(uri: Uri) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val json = SettingsExportCodec.serializeExportData(buildCurrentExportData())
                    importExportRepository.writeToUri(uri, json)
                    userFeedback.emit(UserFeedback("Export complete"))
                } catch (e: Exception) {
                    Log.e(TAG, "Export failed", e)
                    userFeedback.emit(UserFeedback("Failed to export", isError = true))
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
                        userFeedback.emit(UserFeedback("Failed to import: empty file", isError = true))
                        return@launch
                    }
                    applyExportData(SettingsExportCodec.parseExportData(json), replace)
                    userFeedback.emit(UserFeedback("Import complete"))
                } catch (e: Exception) {
                    Log.e(TAG, "Import failed", e)
                    userFeedback.emit(UserFeedback("Failed to import", isError = true))
                }
            }
        }

        /**
         * Starts [exportSyncServer] (HTTP) and [nsdCodeManager] (NSD advertising), and emits a QR
         * code + typeable code for the same session. Call [stopQrExport] when the share dialog closes.
         */
        fun prepareQrExport() {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val json = SettingsExportCodec.serializeExportData(buildCurrentExportData())
                    val host =
                        NetworkUtils.getLocalIpAddress()
                            ?: error("Cannot determine local IP — ensure Wi-Fi is connected")
                    val code = RandomCode.generate()
                    val port = exportSyncServer.start(code, json)
                    nsdCodeManager.startAdvertising(code, port)
                    qrExportReady.emit(QrExportSession(qrText = "locationjoystick://export?host=$host&port=$port&token=$code", code = code))
                } catch (e: Exception) {
                    Log.e(TAG, "QR export preparation failed", e)
                    userFeedback.emit(UserFeedback("Failed to prepare QR export — ensure Wi-Fi is connected", isError = true))
                }
            }
        }

        fun stopQrExport() {
            exportSyncServer.stop()
            nsdCodeManager.stopAdvertising()
        }

        fun onQrScanned(url: String) {
            val uri = Uri.parse(url)
            val host = uri.getQueryParameter("host")
            val port = uri.getQueryParameter("port")?.toIntOrNull()
            val token = uri.getQueryParameter("token")
            viewModelScope.launch {
                if (host == null || port == null || token == null) {
                    Log.e(TAG, "Unrecognized QR code: $url")
                    userFeedback.emit(UserFeedback("Invalid QR code — not a Location Joystick export", isError = true))
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
                    userFeedback.emit(
                        UserFeedback("Code must be ${AppConstants.SyncConstants.GROUP_CODE_LENGTH} characters", isError = true),
                    )
                    return@launch
                }
                val resolved = nsdCodeManager.discoverByCode(normalized)
                if (resolved == null) {
                    userFeedback.emit(UserFeedback("No sender found for code $normalized", isError = true))
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
                userFeedback.emit(
                    UserFeedback("Failed to fetch export — ensure both devices are on the same Wi-Fi", isError = true),
                )
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
                    userFeedback.emit(UserFeedback("Import complete"))
                } catch (e: Exception) {
                    Log.e(TAG, "Import from ExportData failed", e)
                    userFeedback.emit(UserFeedback("Failed to import", isError = true))
                }
            }
        }

        private suspend fun applyExportData(
            data: ExportData,
            replace: Boolean,
        ) {
            withContext(Dispatchers.IO) {
                if (replace) {
                    favoriteRepository.deleteAllFavorites()
                    routeRepository.deleteAllRoutes()
                }
                data.favoriteLocations.forEach { fav ->
                    favoriteRepository.addFavorite(
                        id = fav.id,
                        name = fav.name,
                        position = fav.position,
                        createdAt = fav.createdAt,
                    )
                }
                data.routes.forEach { routeRepository.insertRoute(it).getOrNull() }
            }
            // Preserve fields not present in ExportData by reading current persisted values.
            val currentSnapshot = settingsRepository.getSettingsSnapshot().first()
            val profileById = data.speedProfiles.associateBy { it.id }
            settingsRepository.applySnapshot(
                currentSnapshot.copy(
                    walkSpeedMs = profileById["walk"]?.speedMetersPerSecond ?: currentSnapshot.walkSpeedMs,
                    runSpeedMs = profileById["run"]?.speedMetersPerSecond ?: currentSnapshot.runSpeedMs,
                    bikeSpeedMs = profileById["bike"]?.speedMetersPerSecond ?: currentSnapshot.bikeSpeedMs,
                    speedUnit = data.settings.speedUnit,
                    featureOrder = data.settings.featureOrder,
                    enabledWidgetFeatures = data.settings.enabledWidgetFeatures,
                    enabledMapFeatures = data.settings.enabledMapFeatures,
                    jitterIdleRadius = data.jitterIdleRadius,
                    jitterMovingRadius = data.jitterMovingRadius,
                    jitterIntervalSeconds = data.jitterIntervalSeconds,
                    jitterIdleIntervalSeconds = data.jitterIdleIntervalSeconds,
                    realismBearingHoldIdle = data.settings.bearingHoldOnIdle,
                    realismAltitudeEnabled = data.settings.altitudeEnabled,
                    realismWarmupEnabled = data.settings.warmupEnabled,
                    realismSatelliteExtrasEnabled = data.settings.satelliteExtrasEnabled,
                    realismSuspendedMockingEnabled = data.settings.suspendedMockingEnabled,
                    realismPedometerMockingEnabled = data.settings.pedometerMockingEnabled,
                    jitterSpeedIdleVariationPct = data.jitterSpeedIdleVariationPct,
                    jitterSpeedMovingVariationPct = data.jitterSpeedMovingVariationPct,
                    hotLocationsEnabled = data.hotLocationsEnabled,
                    selectedHotLocationIds = data.selectedHotLocationIds,
                    hotRoutesEnabled = data.hotRoutesEnabled,
                    selectedHotRouteIds = data.selectedHotRouteIds,
                    roamingDefaults = data.settings.roamingDefaults,
                ),
            )
            if (data.hotLocationsEnabled) {
                favoriteRepository.upsertHotLocations(data.selectedHotLocationIds)
            } else {
                favoriteRepository.removeHotLocations()
            }
            if (data.hotRoutesEnabled) {
                routeRepository.upsertHotRoutes(data.selectedHotRouteIds)
            } else {
                routeRepository.removeHotRoutes()
            }
            settingsRepository.setRoutesSortNewestFirst(data.routesSortNewestFirst)
            settingsRepository.setFavoritesSortNewestFirst(data.favoritesSortNewestFirst)
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
                        userFeedback.emit(UserFeedback("Failed to import from GPS Joystick", isError = true))
                        return@launch
                    }
                    val result = GpsJoystickMigrator.parse(bytes)
                    if (result.isFailure) {
                        Log.e(TAG, "GPS Joystick import failed: ${result.exceptionOrNull()?.message}")
                        userFeedback.emit(UserFeedback("Failed to import from GPS Joystick", isError = true))
                        return@launch
                    }
                    val migration = result.getOrNull() ?: return@launch
                    withContext(Dispatchers.IO) {
                        if (replace) {
                            favoriteRepository.deleteAllFavorites()
                            routeRepository.deleteAllRoutes()
                        }
                        migration.favorites.forEach { fav ->
                            favoriteRepository.addFavorite(
                                id = fav.id,
                                name = fav.name,
                                position = fav.position,
                                createdAt = fav.createdAt,
                            )
                        }
                        migration.routes.forEach { routeRepository.insertRoute(it).getOrNull() }
                    }
                    Log.i(TAG, "GPS Joystick import complete: ${migration.favorites.size} favorites, ${migration.routes.size} routes")
                    userFeedback.emit(
                        UserFeedback("Imported ${migration.favorites.size} favorites, ${migration.routes.size} routes from GPS Joystick"),
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "GPS Joystick import failed", e)
                    userFeedback.emit(UserFeedback("Failed to import from GPS Joystick", isError = true))
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
                        userFeedback.emit(UserFeedback("Failed to import from YAMLA", isError = true))
                        return@launch
                    }
                    val result = YamlaMigrator.parse(json)
                    if (result.isFailure) {
                        Log.e(TAG, "YAMLA import failed: ${result.exceptionOrNull()?.message}")
                        userFeedback.emit(UserFeedback("Failed to import from YAMLA", isError = true))
                        return@launch
                    }
                    val migration = result.getOrNull() ?: return@launch
                    withContext(Dispatchers.IO) {
                        if (replace) {
                            favoriteRepository.deleteAllFavorites()
                        }
                        migration.favorites.forEach { fav ->
                            favoriteRepository.addFavorite(
                                id = fav.id,
                                name = fav.name,
                                position = fav.position,
                                createdAt = fav.createdAt,
                            )
                        }
                    }
                    migration.walkSpeed?.let { settingsRepository.setWalkSpeed(it) }
                    migration.runSpeed?.let { settingsRepository.setRunSpeed(it) }
                    migration.bikeSpeed?.let { settingsRepository.setBikeSpeed(it) }
                    val speedsMsg = if (migration.walkSpeed != null) ", speeds updated" else ""
                    Log.i(TAG, "YAMLA import complete: ${migration.favorites.size} favorites$speedsMsg")
                    userFeedback.emit(UserFeedback("Imported ${migration.favorites.size} favorites from YAMLA$speedsMsg"))
                } catch (e: Exception) {
                    Log.e(TAG, "YAMLA import failed", e)
                    userFeedback.emit(UserFeedback("Failed to import from YAMLA", isError = true))
                }
            }
        }
    }
