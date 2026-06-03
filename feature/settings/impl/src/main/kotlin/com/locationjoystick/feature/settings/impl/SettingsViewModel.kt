package com.locationjoystick.feature.settings.impl

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.root.RootCapabilityChecker
import com.locationjoystick.core.common.root.SensorPermissionBootstrap
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.datastore.SettingsSnapshot
import com.locationjoystick.core.model.AppSettings
import com.locationjoystick.core.model.ExportData
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature
import dagger.hilt.android.lifecycle.HiltViewModel
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
        private val rootCapabilityChecker: RootCapabilityChecker,
        private val sensorPermissionBootstrap: SensorPermissionBootstrap,
        private val importExportRepository: ImportExportRepository,
    ) : ViewModel() {
        companion object {
            private const val TAG = "SettingsViewModel"
        }

        private val _isRooted = MutableStateFlow(false)
        val isRooted: StateFlow<Boolean> = _isRooted.asStateFlow()

        init {
            _isRooted.value = rootCapabilityChecker.isRooted()
        }

        internal data class UserFeedback(
            val message: String,
            val isError: Boolean = false,
        )

        private data class ChunkSession(
            val total: Int,
            val chunks: MutableMap<Int, List<ChunkContent>>,
        )

        private val chunkSessions = mutableMapOf<String, ChunkSession>()

        private val _qrImportReady = MutableSharedFlow<ExportData>(extraBufferCapacity = 1)
        val qrImportReady: SharedFlow<ExportData> = _qrImportReady

        private val _qrScanProgress = MutableStateFlow<Pair<Int, Int>?>(null)
        val qrScanProgress: StateFlow<Pair<Int, Int>?> = _qrScanProgress.asStateFlow()

        internal val qrChunksReady = MutableSharedFlow<QrChunker.ChunkResult>(extraBufferCapacity = 1)

        internal val userFeedback = MutableSharedFlow<UserFeedback>(extraBufferCapacity = 1)

        private data class DraftState(
            val walkSpeed: Double? = null,
            val runSpeed: Double? = null,
            val bikeSpeed: Double? = null,
            val speedUnit: SpeedUnit? = null,
            val widgetFeatures: Set<WidgetFeature>? = null,
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
            val jitterSpeedIdleVariationPct: Int? = null,
            val jitterSpeedMovingVariationPct: Int? = null,
            val elevationTiltJitterDegrees: Float? = null,
            val elevationNoiseAmplitudeMs2: Float? = null,
            val hotLocationsEnabled: Boolean? = null,
        )

        private val mutableDraft = MutableStateFlow(DraftState())

        private val repoStateFlow = settingsRepository.getSettingsSnapshot()

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

        val uiState: StateFlow<SettingsUiState> =
            combine(
                repoStateFlow,
                draftStateFlow,
            ) { repoState, draftState ->
                val isDirty = draftState != DraftState()
                SettingsUiState(
                    isLoading = false,
                    walkSpeed = draftState.walkSpeed ?: repoState.walkSpeedMs,
                    runSpeed = draftState.runSpeed ?: repoState.runSpeedMs,
                    bikeSpeed = draftState.bikeSpeed ?: repoState.bikeSpeedMs,
                    speedUnit = draftState.speedUnit ?: repoState.speedUnit,
                    enabledWidgetFeatures = draftState.widgetFeatures ?: repoState.widgetFeatures,
                    rememberLastLocation = draftState.rememberLastLocation ?: repoState.rememberLastLocation,
                    mapFollowsLocation = draftState.mapFollowsLocation ?: repoState.mapFollowsLocation,
                    jitterIdleRadiusMeters = draftState.jitterIdleRadius ?: repoState.jitterIdleRadius,
                    jitterMovingRadiusMeters = draftState.jitterMovingRadius ?: repoState.jitterMovingRadius,
                    jitterIntervalSeconds = draftState.jitterIntervalSeconds ?: repoState.jitterIntervalSeconds,
                    jitterIdleIntervalSeconds = draftState.jitterIdleIntervalSeconds ?: repoState.jitterIdleIntervalSeconds,
                    realismBearingHoldIdle = draftState.realismBearingHoldIdle ?: repoState.realismBearingHoldIdle,
                    realismAltitudeEnabled = draftState.realismAltitudeEnabled ?: repoState.realismAltitudeEnabled,
                    realismWarmupEnabled = draftState.realismWarmupEnabled ?: repoState.realismWarmupEnabled,
                    realismSatelliteExtrasEnabled = draftState.realismSatelliteExtrasEnabled ?: repoState.realismSatelliteExtrasEnabled,
                    realismSuspendedMockingEnabled = draftState.realismSuspendedMockingEnabled ?: repoState.realismSuspendedMockingEnabled,
                    jitterSpeedIdleVariationPct = draftState.jitterSpeedIdleVariationPct ?: repoState.jitterSpeedIdleVariationPct,
                    jitterSpeedMovingVariationPct = draftState.jitterSpeedMovingVariationPct ?: repoState.jitterSpeedMovingVariationPct,
                    elevationTiltJitterDegrees = draftState.elevationTiltJitterDegrees ?: repoState.elevationTiltJitterDegrees,
                    elevationNoiseAmplitudeMs2 = draftState.elevationNoiseAmplitudeMs2 ?: repoState.elevationNoiseAmplitudeMs2,
                    hotLocationsEnabled = draftState.hotLocationsEnabled ?: repoState.hotLocationsEnabled,
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

        fun setWidgetFeatures(features: Set<WidgetFeature>) {
            mutableDraft.update { it.copy(widgetFeatures = features) }
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

        fun setJitterSpeedIdleVariationPct(pct: Int) {
            mutableDraft.update { it.copy(jitterSpeedIdleVariationPct = pct) }
        }

        fun setJitterSpeedMovingVariationPct(pct: Int) {
            mutableDraft.update { it.copy(jitterSpeedMovingVariationPct = pct) }
        }

        fun setElevationTiltJitterDegrees(degrees: Float) {
            mutableDraft.update { it.copy(elevationTiltJitterDegrees = degrees) }
        }

        fun setElevationNoiseAmplitudeMs2(amplitude: Float) {
            mutableDraft.update { it.copy(elevationNoiseAmplitudeMs2 = amplitude) }
        }

        fun setHotLocationsEnabled(enabled: Boolean) {
            mutableDraft.update { it.copy(hotLocationsEnabled = enabled) }
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
                            widgetFeatures = state.enabledWidgetFeatures,
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
                            jitterSpeedIdleVariationPct = state.jitterSpeedIdleVariationPct,
                            jitterSpeedMovingVariationPct = state.jitterSpeedMovingVariationPct,
                            elevationTiltJitterDegrees = state.elevationTiltJitterDegrees,
                            elevationNoiseAmplitudeMs2 = state.elevationNoiseAmplitudeMs2,
                            hotLocationsEnabled = state.hotLocationsEnabled,
                        ),
                    )
                    if (d.roamingDefaults != null) settingsRepository.updateRoamingDefaults(d.roamingDefaults)
                    if (d.hotLocationsEnabled != null) {
                        if (d.hotLocationsEnabled) {
                            favoriteRepository.upsertHotLocations()
                        } else {
                            favoriteRepository.removeHotLocations()
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

        fun requestElevationAccess() {
            viewModelScope.launch {
                sensorPermissionBootstrap.grantIfNeeded()
            }
        }

        fun convertMsToDisplay(
            ms: Double,
            unit: SpeedUnit,
        ): Double =
            when (unit) {
                SpeedUnit.KMH -> ms * 3.6
                SpeedUnit.MPH -> ms * 2.237
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
                    enabledWidgetFeatures = state.enabledWidgetFeatures.toList(),
                    bearingHoldOnIdle = state.realismBearingHoldIdle,
                    altitudeEnabled = state.realismAltitudeEnabled,
                    warmupEnabled = state.realismWarmupEnabled,
                    satelliteExtrasEnabled = state.realismSatelliteExtrasEnabled,
                    suspendedMockingEnabled = state.realismSuspendedMockingEnabled,
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
                elevationTiltJitterDegrees = state.elevationTiltJitterDegrees,
                elevationNoiseAmplitudeMs2 = state.elevationNoiseAmplitudeMs2,
                hotLocationsEnabled = state.hotLocationsEnabled,
            )
        }

        fun writeExportToUri(uri: Uri) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val json = serializeExportData(buildCurrentExportData())
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
                    applyExportData(parseExportData(json), replace)
                    userFeedback.emit(UserFeedback("Import complete"))
                } catch (e: Exception) {
                    Log.e(TAG, "Import failed", e)
                    userFeedback.emit(UserFeedback("Failed to import", isError = true))
                }
            }
        }

        fun prepareQrChunks() {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    qrChunksReady.emit(QrChunker.chunk(buildCurrentExportData()))
                } catch (e: Exception) {
                    Log.e(TAG, "QR chunk preparation failed", e)
                    userFeedback.emit(UserFeedback("Failed to prepare QR export", isError = true))
                }
            }
        }

        fun onChunkScanned(envelope: ChunkEnvelope) {
            viewModelScope.launch {
                if (envelope.k != "lj.s" || envelope.v != 2) {
                    Log.e(TAG, "Unknown QR format: k=${envelope.k} v=${envelope.v}")
                    userFeedback.emit(UserFeedback("Invalid QR code — not a Location Joystick export", isError = true))
                    return@launch
                }
                try {
                    val content = withContext(Dispatchers.Default) { decodeChunkContent(envelope.d) }
                    val session =
                        chunkSessions.getOrPut(envelope.session) {
                            ChunkSession(envelope.total, mutableMapOf())
                        }
                    session.chunks[envelope.chunk] = content
                    _qrScanProgress.value = session.chunks.size to session.total
                    if (session.chunks.size == session.total) {
                        chunkSessions.remove(envelope.session)
                        _qrScanProgress.value = null
                        val merged = withContext(Dispatchers.Default) { mergeChunks(session.chunks.values.flatten()) }
                        _qrImportReady.emit(merged)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "QR chunk decode failed", e)
                    userFeedback.emit(UserFeedback("Failed to read QR code — try scanning again", isError = true))
                }
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
            data.speedProfiles.forEach { profile ->
                when (profile.id) {
                    "walk" -> settingsRepository.setWalkSpeed(profile.speedMetersPerSecond)
                    "run" -> settingsRepository.setRunSpeed(profile.speedMetersPerSecond)
                    "bike" -> settingsRepository.setBikeSpeed(profile.speedMetersPerSecond)
                }
            }
            settingsRepository.setSpeedUnit(data.settings.speedUnit)
            settingsRepository.setWidgetFeatures(data.settings.enabledWidgetFeatures)
            settingsRepository.updateRoamingDefaults(data.settings.roamingDefaults)
            settingsRepository.setRealismBearingHoldIdle(data.settings.bearingHoldOnIdle)
            settingsRepository.setRealismAltitudeEnabled(data.settings.altitudeEnabled)
            settingsRepository.setRealismWarmupEnabled(data.settings.warmupEnabled)
            settingsRepository.setRealismSatelliteExtrasEnabled(data.settings.satelliteExtrasEnabled)
            settingsRepository.setRealismSuspendedMockingEnabled(data.settings.suspendedMockingEnabled)
            settingsRepository.setJitterIdleRadius(data.jitterIdleRadius)
            settingsRepository.setJitterMovingRadius(data.jitterMovingRadius)
            settingsRepository.setJitterIntervalSeconds(data.jitterIntervalSeconds)
            settingsRepository.setJitterIdleIntervalSeconds(data.jitterIdleIntervalSeconds)
            settingsRepository.setJitterSpeedIdleVariationPct(data.jitterSpeedIdleVariationPct)
            settingsRepository.setJitterSpeedMovingVariationPct(data.jitterSpeedMovingVariationPct)
            settingsRepository.setElevationTiltJitterDegrees(data.elevationTiltJitterDegrees)
            settingsRepository.setElevationNoiseAmplitudeMs2(data.elevationNoiseAmplitudeMs2)
            settingsRepository.setHotLocationsEnabled(data.hotLocationsEnabled)
            if (data.hotLocationsEnabled) {
                favoriteRepository.upsertHotLocations()
            } else {
                favoriteRepository.removeHotLocations()
            }
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

        private fun decodeChunkContent(encoded: String): List<ChunkContent> =
            decodeChunkEnvelope(ChunkEnvelope(session = "", chunk = 0, total = 0, d = encoded))

        private fun mergeChunks(allContent: List<ChunkContent>): ExportData = mergeChunkContents(allContent)

        private fun serializeExportData(data: ExportData) = SettingsExportCodec.serializeExportData(data)

        private fun parseExportData(json: String) = SettingsExportCodec.parseExportData(json)
    }
