package com.locationjoystick.feature.settings.impl

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val favoriteRepository: FavoriteRepository,
        private val routeRepository: RouteRepository,
    ) : ViewModel() {
        companion object {
            private const val TAG = "SettingsViewModel"
        }

        private data class ChunkSession(
            val total: Int,
            val chunks: MutableMap<Int, List<ChunkContent>>,
        )

        private val chunkSessions = mutableMapOf<String, ChunkSession>()

        private val _qrImportReady = MutableSharedFlow<ExportData>(extraBufferCapacity = 1)
        val qrImportReady: SharedFlow<ExportData> = _qrImportReady

        private val _qrChunksReady = MutableSharedFlow<QrChunker.ChunkResult>(extraBufferCapacity = 1)
        internal val qrChunksReady: SharedFlow<QrChunker.ChunkResult> = _qrChunksReady

        private data class RepoRealismChunk(
            val mapFollowsLocation: Boolean,
            val bearingHoldIdle: Boolean,
            val altitudeEnabled: Boolean,
            val warmupEnabled: Boolean,
            val satelliteExtrasEnabled: Boolean,
            val suspendedMockingEnabled: Boolean,
        )

        private data class DraftRealismChunk(
            val mapFollowsLocation: Boolean?,
            val roamingDefaults: RoamingDefaults?,
            val bearingHoldIdle: Boolean?,
            val altitudeEnabled: Boolean?,
            val warmupEnabled: Boolean?,
            val satelliteExtrasEnabled: Boolean?,
            val suspendedMockingEnabled: Boolean?,
        )

        private data class RepoState(
            val walkSpeed: Double,
            val runSpeed: Double,
            val bikeSpeed: Double,
            val speedUnit: SpeedUnit,
            val widgetFeatures: Set<WidgetFeature>,
            val rememberLastLocation: Boolean,
            val mapFollowsLocation: Boolean,
            val jitterIdleRadius: Double,
            val jitterMovingRadius: Double,
            val jitterIntervalSeconds: Int,
            val realismBearingHoldIdle: Boolean,
            val realismAltitudeEnabled: Boolean,
            val realismWarmupEnabled: Boolean,
            val realismSatelliteExtrasEnabled: Boolean,
            val realismSuspendedMockingEnabled: Boolean,
        )

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
            val roamingDefaults: RoamingDefaults? = null,
            val realismBearingHoldIdle: Boolean? = null,
            val realismAltitudeEnabled: Boolean? = null,
            val realismWarmupEnabled: Boolean? = null,
            val realismSatelliteExtrasEnabled: Boolean? = null,
            val realismSuspendedMockingEnabled: Boolean? = null,
        )

        private val draftWalkSpeedFlow = MutableStateFlow<Double?>(null)
        private val draftRunSpeedFlow = MutableStateFlow<Double?>(null)
        private val draftBikeSpeedFlow = MutableStateFlow<Double?>(null)
        private val draftSpeedUnitFlow = MutableStateFlow<SpeedUnit?>(null)
        private val draftWidgetFeaturesFlow = MutableStateFlow<Set<WidgetFeature>?>(null)
        private val draftRememberLastLocationFlow = MutableStateFlow<Boolean?>(null)
        private val draftMapFollowsLocationFlow = MutableStateFlow<Boolean?>(null)
        private val draftJitterIdleRadiusFlow = MutableStateFlow<Double?>(null)
        private val draftJitterMovingRadiusFlow = MutableStateFlow<Double?>(null)
        private val draftJitterIntervalSecondsFlow = MutableStateFlow<Int?>(null)
        private val draftRoamingDefaultsFlow = MutableStateFlow<RoamingDefaults?>(null)
        private val draftRealismBearingHoldIdleFlow = MutableStateFlow<Boolean?>(null)
        private val draftRealismAltitudeEnabledFlow = MutableStateFlow<Boolean?>(null)
        private val draftRealismWarmupEnabledFlow = MutableStateFlow<Boolean?>(null)
        private val draftRealismSatelliteExtrasEnabledFlow = MutableStateFlow<Boolean?>(null)
        private val draftRealismSuspendedMockingEnabledFlow = MutableStateFlow<Boolean?>(null)

        private val repoStateFlow =
            combine(
                combine(
                    settingsRepository.getWalkSpeed(),
                    settingsRepository.getRunSpeed(),
                    settingsRepository.getBikeSpeed(),
                ) { walkSpeed, runSpeed, bikeSpeed ->
                    Triple(walkSpeed, runSpeed, bikeSpeed)
                },
                combine(
                    settingsRepository.getSpeedUnit(),
                    settingsRepository.getWidgetFeatures(),
                    settingsRepository.getRememberLastLocation(),
                ) { speedUnit, features, rememberLastLocation ->
                    Triple(speedUnit, features, rememberLastLocation)
                },
                combine(
                    settingsRepository.getJitterIdleRadius(),
                    settingsRepository.getJitterMovingRadius(),
                    settingsRepository.getJitterIntervalSeconds(),
                ) { idle, moving, interval -> Triple(idle, moving, interval) },
                combine(
                    settingsRepository.getMapFollowsLocation(),
                    combine(
                        settingsRepository.getRealismBearingHoldIdle(),
                        settingsRepository.getRealismAltitudeEnabled(),
                        settingsRepository.getRealismWarmupEnabled(),
                    ) { bearing, altitude, warmup -> Triple(bearing, altitude, warmup) },
                    combine(
                        settingsRepository.getRealismSatelliteExtrasEnabled(),
                        settingsRepository.getRealismSuspendedMockingEnabled(),
                    ) { satellites, suspended -> Pair(satellites, suspended) },
                ) { mapFollows, realism1, realism2 ->
                    RepoRealismChunk(
                        mapFollowsLocation = mapFollows,
                        bearingHoldIdle = realism1.first,
                        altitudeEnabled = realism1.second,
                        warmupEnabled = realism1.third,
                        satelliteExtrasEnabled = realism2.first,
                        suspendedMockingEnabled = realism2.second,
                    )
                },
            ) { speeds, settings, jitter, chunk ->
                RepoState(
                    walkSpeed = speeds.first,
                    runSpeed = speeds.second,
                    bikeSpeed = speeds.third,
                    speedUnit = settings.first,
                    widgetFeatures = settings.second.toSet(),
                    rememberLastLocation = settings.third,
                    mapFollowsLocation = chunk.mapFollowsLocation,
                    jitterIdleRadius = jitter.first,
                    jitterMovingRadius = jitter.second,
                    jitterIntervalSeconds = jitter.third,
                    realismBearingHoldIdle = chunk.bearingHoldIdle,
                    realismAltitudeEnabled = chunk.altitudeEnabled,
                    realismWarmupEnabled = chunk.warmupEnabled,
                    realismSatelliteExtrasEnabled = chunk.satelliteExtrasEnabled,
                    realismSuspendedMockingEnabled = chunk.suspendedMockingEnabled,
                )
            }

        private val draftStateFlow =
            combine(
                combine(
                    draftWalkSpeedFlow.asStateFlow(),
                    draftRunSpeedFlow.asStateFlow(),
                    draftBikeSpeedFlow.asStateFlow(),
                ) { walk, run, bike ->
                    Triple(walk, run, bike)
                },
                combine(
                    draftSpeedUnitFlow.asStateFlow(),
                    draftWidgetFeaturesFlow.asStateFlow(),
                    draftRememberLastLocationFlow.asStateFlow(),
                ) { unit, features, remember ->
                    Triple(unit, features, remember)
                },
                combine(
                    draftJitterIdleRadiusFlow.asStateFlow(),
                    draftJitterMovingRadiusFlow.asStateFlow(),
                    draftJitterIntervalSecondsFlow.asStateFlow(),
                ) { idle, moving, interval -> Triple(idle, moving, interval) },
                combine(
                    draftRoamingDefaultsFlow.asStateFlow(),
                    draftMapFollowsLocationFlow.asStateFlow(),
                    combine(
                        draftRealismBearingHoldIdleFlow.asStateFlow(),
                        draftRealismAltitudeEnabledFlow.asStateFlow(),
                        draftRealismWarmupEnabledFlow.asStateFlow(),
                    ) { bearing, altitude, warmup -> Triple(bearing, altitude, warmup) },
                    combine(
                        draftRealismSatelliteExtrasEnabledFlow.asStateFlow(),
                        draftRealismSuspendedMockingEnabledFlow.asStateFlow(),
                    ) { satellites, suspended -> Pair(satellites, suspended) },
                ) { roaming, mapFollows, realism1, realism2 ->
                    DraftRealismChunk(
                        mapFollowsLocation = mapFollows,
                        roamingDefaults = roaming,
                        bearingHoldIdle = realism1.first,
                        altitudeEnabled = realism1.second,
                        warmupEnabled = realism1.third,
                        satelliteExtrasEnabled = realism2.first,
                        suspendedMockingEnabled = realism2.second,
                    )
                },
            ) { speeds, settings, jitter, chunk ->
                DraftState(
                    walkSpeed = speeds.first,
                    runSpeed = speeds.second,
                    bikeSpeed = speeds.third,
                    speedUnit = settings.first,
                    widgetFeatures = settings.second,
                    rememberLastLocation = settings.third,
                    mapFollowsLocation = chunk.mapFollowsLocation,
                    jitterIdleRadius = jitter.first,
                    jitterMovingRadius = jitter.second,
                    jitterIntervalSeconds = jitter.third,
                    roamingDefaults = chunk.roamingDefaults,
                    realismBearingHoldIdle = chunk.bearingHoldIdle,
                    realismAltitudeEnabled = chunk.altitudeEnabled,
                    realismWarmupEnabled = chunk.warmupEnabled,
                    realismSatelliteExtrasEnabled = chunk.satelliteExtrasEnabled,
                    realismSuspendedMockingEnabled = chunk.suspendedMockingEnabled,
                )
            }

        val roamingDefaults: StateFlow<RoamingDefaults> =
            combine(
                settingsRepository.getRoamingDefaults(),
                draftRoamingDefaultsFlow,
            ) { repo, draft -> draft ?: repo }
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
                val isDirty =
                    draftState.walkSpeed != null || draftState.runSpeed != null ||
                        draftState.bikeSpeed != null || draftState.speedUnit != null ||
                        draftState.widgetFeatures != null || draftState.rememberLastLocation != null ||
                        draftState.mapFollowsLocation != null ||
                        draftState.jitterIdleRadius != null || draftState.jitterMovingRadius != null ||
                        draftState.jitterIntervalSeconds != null || draftState.roamingDefaults != null ||
                        draftState.realismBearingHoldIdle != null || draftState.realismAltitudeEnabled != null ||
                        draftState.realismWarmupEnabled != null || draftState.realismSatelliteExtrasEnabled != null ||
                        draftState.realismSuspendedMockingEnabled != null
                SettingsUiState(
                    isLoading = false,
                    walkSpeed = draftState.walkSpeed ?: repoState.walkSpeed,
                    runSpeed = draftState.runSpeed ?: repoState.runSpeed,
                    bikeSpeed = draftState.bikeSpeed ?: repoState.bikeSpeed,
                    speedUnit = draftState.speedUnit ?: repoState.speedUnit,
                    enabledWidgetFeatures = draftState.widgetFeatures ?: repoState.widgetFeatures,
                    rememberLastLocation = draftState.rememberLastLocation ?: repoState.rememberLastLocation,
                    mapFollowsLocation = draftState.mapFollowsLocation ?: repoState.mapFollowsLocation,
                    jitterIdleRadiusMeters = draftState.jitterIdleRadius ?: repoState.jitterIdleRadius,
                    jitterMovingRadiusMeters = draftState.jitterMovingRadius ?: repoState.jitterMovingRadius,
                    jitterIntervalSeconds = draftState.jitterIntervalSeconds ?: repoState.jitterIntervalSeconds,
                    realismBearingHoldIdle = draftState.realismBearingHoldIdle ?: repoState.realismBearingHoldIdle,
                    realismAltitudeEnabled = draftState.realismAltitudeEnabled ?: repoState.realismAltitudeEnabled,
                    realismWarmupEnabled = draftState.realismWarmupEnabled ?: repoState.realismWarmupEnabled,
                    realismSatelliteExtrasEnabled = draftState.realismSatelliteExtrasEnabled ?: repoState.realismSatelliteExtrasEnabled,
                    realismSuspendedMockingEnabled = draftState.realismSuspendedMockingEnabled ?: repoState.realismSuspendedMockingEnabled,
                    isDirty = isDirty,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(isLoading = true),
            )

        fun setWalkSpeed(displaySpeed: Double) {
            val ms = convertDisplayToMs(displaySpeed, uiState.value.speedUnit)
            draftWalkSpeedFlow.value = ms
        }

        fun setRunSpeed(displaySpeed: Double) {
            val ms = convertDisplayToMs(displaySpeed, uiState.value.speedUnit)
            draftRunSpeedFlow.value = ms
        }

        fun setBikeSpeed(displaySpeed: Double) {
            val ms = convertDisplayToMs(displaySpeed, uiState.value.speedUnit)
            draftBikeSpeedFlow.value = ms
        }

        fun setSpeedUnit(unit: SpeedUnit) {
            draftSpeedUnitFlow.value = unit
        }

        fun setWidgetFeatures(features: Set<WidgetFeature>) {
            draftWidgetFeaturesFlow.value = features
        }

        fun setRememberLastLocation(enabled: Boolean) {
            draftRememberLastLocationFlow.value = enabled
        }

        fun setMapFollowsLocation(enabled: Boolean) {
            draftMapFollowsLocationFlow.value = enabled
        }

        fun setJitterIdleRadius(meters: Double) {
            draftJitterIdleRadiusFlow.value = meters
        }

        fun setJitterMovingRadius(meters: Double) {
            draftJitterMovingRadiusFlow.value = meters
        }

        fun setJitterIntervalSeconds(seconds: Int) {
            draftJitterIntervalSecondsFlow.value = seconds
        }

        fun updateRoamingDefaults(defaults: RoamingDefaults) {
            draftRoamingDefaultsFlow.value = defaults
        }

        fun setRealismBearingHoldIdle(v: Boolean) {
            draftRealismBearingHoldIdleFlow.value = v
        }

        fun setRealismAltitudeEnabled(v: Boolean) {
            draftRealismAltitudeEnabledFlow.value = v
        }

        fun setRealismWarmupEnabled(v: Boolean) {
            draftRealismWarmupEnabledFlow.value = v
        }

        fun setRealismSatelliteExtrasEnabled(v: Boolean) {
            draftRealismSatelliteExtrasEnabledFlow.value = v
        }

        fun setRealismSuspendedMockingEnabled(v: Boolean) {
            draftRealismSuspendedMockingEnabledFlow.value = v
        }

        fun saveChanges() {
            viewModelScope.launch {
                val draftWalk = draftWalkSpeedFlow.value
                val draftRun = draftRunSpeedFlow.value
                val draftBike = draftBikeSpeedFlow.value
                val draftUnit = draftSpeedUnitFlow.value
                val draftFeatures = draftWidgetFeaturesFlow.value
                val draftRememberLastLocation = draftRememberLastLocationFlow.value

                if (draftWalk != null) {
                    settingsRepository.setWalkSpeed(draftWalk)
                    draftWalkSpeedFlow.value = null
                }
                if (draftRun != null) {
                    settingsRepository.setRunSpeed(draftRun)
                    draftRunSpeedFlow.value = null
                }
                if (draftBike != null) {
                    settingsRepository.setBikeSpeed(draftBike)
                    draftBikeSpeedFlow.value = null
                }
                if (draftUnit != null) {
                    settingsRepository.setSpeedUnit(draftUnit)
                    draftSpeedUnitFlow.value = null
                }
                if (draftFeatures != null) {
                    settingsRepository.setWidgetFeatures(draftFeatures.toList())
                    draftWidgetFeaturesFlow.value = null
                }
                if (draftRememberLastLocation != null) {
                    settingsRepository.setRememberLastLocation(draftRememberLastLocation)
                    draftRememberLastLocationFlow.value = null
                }
                val draftMapFollows = draftMapFollowsLocationFlow.value
                if (draftMapFollows != null) {
                    settingsRepository.setMapFollowsLocation(draftMapFollows)
                    draftMapFollowsLocationFlow.value = null
                }
                val draftJitterIdle = draftJitterIdleRadiusFlow.value
                if (draftJitterIdle != null) {
                    settingsRepository.setJitterIdleRadius(draftJitterIdle)
                    draftJitterIdleRadiusFlow.value = null
                }
                val draftJitterMoving = draftJitterMovingRadiusFlow.value
                if (draftJitterMoving != null) {
                    settingsRepository.setJitterMovingRadius(draftJitterMoving)
                    draftJitterMovingRadiusFlow.value = null
                }
                val draftJitterInterval = draftJitterIntervalSecondsFlow.value
                if (draftJitterInterval != null) {
                    settingsRepository.setJitterIntervalSeconds(draftJitterInterval)
                    draftJitterIntervalSecondsFlow.value = null
                }
                val draftRoaming = draftRoamingDefaultsFlow.value
                if (draftRoaming != null) {
                    settingsRepository.updateRoamingDefaults(draftRoaming)
                    draftRoamingDefaultsFlow.value = null
                }
                val draftBearingHoldIdle = draftRealismBearingHoldIdleFlow.value
                if (draftBearingHoldIdle != null) {
                    settingsRepository.setRealismBearingHoldIdle(draftBearingHoldIdle)
                    draftRealismBearingHoldIdleFlow.value = null
                }
                val draftAltitudeEnabled = draftRealismAltitudeEnabledFlow.value
                if (draftAltitudeEnabled != null) {
                    settingsRepository.setRealismAltitudeEnabled(draftAltitudeEnabled)
                    draftRealismAltitudeEnabledFlow.value = null
                }
                val draftWarmupEnabled = draftRealismWarmupEnabledFlow.value
                if (draftWarmupEnabled != null) {
                    settingsRepository.setRealismWarmupEnabled(draftWarmupEnabled)
                    draftRealismWarmupEnabledFlow.value = null
                }
                val draftSatelliteExtrasEnabled = draftRealismSatelliteExtrasEnabledFlow.value
                if (draftSatelliteExtrasEnabled != null) {
                    settingsRepository.setRealismSatelliteExtrasEnabled(draftSatelliteExtrasEnabled)
                    draftRealismSatelliteExtrasEnabledFlow.value = null
                }
                val draftSuspendedMockingEnabled = draftRealismSuspendedMockingEnabledFlow.value
                if (draftSuspendedMockingEnabled != null) {
                    settingsRepository.setRealismSuspendedMockingEnabled(draftSuspendedMockingEnabled)
                    draftRealismSuspendedMockingEnabledFlow.value = null
                }
            }
        }

        fun discardChanges() {
            draftWalkSpeedFlow.value = null
            draftRunSpeedFlow.value = null
            draftBikeSpeedFlow.value = null
            draftSpeedUnitFlow.value = null
            draftWidgetFeaturesFlow.value = null
            draftRememberLastLocationFlow.value = null
            draftMapFollowsLocationFlow.value = null
            draftJitterIdleRadiusFlow.value = null
            draftJitterMovingRadiusFlow.value = null
            draftJitterIntervalSecondsFlow.value = null
            draftRoamingDefaultsFlow.value = null
            draftRealismBearingHoldIdleFlow.value = null
            draftRealismAltitudeEnabledFlow.value = null
            draftRealismWarmupEnabledFlow.value = null
            draftRealismSatelliteExtrasEnabledFlow.value = null
            draftRealismSuspendedMockingEnabledFlow.value = null
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

        fun writeExportToUri(
            context: Context,
            uri: Uri,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
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
                    val routes = routeRepository.getRoutes().first()
                    val favorites = favoriteRepository.getFavorites().first()

                    val exportData =
                        ExportData(
                            schemaVersion = AppConstants.ExportConstants.SCHEMA_VERSION,
                            exportedAt = System.currentTimeMillis(),
                            settings = settings,
                            speedProfiles = speedProfiles,
                            routes = routes,
                            favoriteLocations = favorites,
                            jitterIdleRadius = state.jitterIdleRadiusMeters,
                            jitterMovingRadius = state.jitterMovingRadiusMeters,
                            jitterIntervalSeconds = state.jitterIntervalSeconds,
                        )

                    val json = serializeExportData(exportData)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Export failed", e)
                }
            }
        }

        fun importSettings(
            context: Context,
            uri: Uri,
            replace: Boolean = true,
        ) {
            viewModelScope.launch {
                try {
                    val json =
                        withContext(Dispatchers.IO) {
                            context.contentResolver
                                .openInputStream(uri)
                                ?.bufferedReader()
                                ?.readText() ?: ""
                        }
                    if (json.isEmpty()) {
                        Log.e(TAG, "Import failed: empty file")
                        return@launch
                    }
                    val exportData = parseExportData(json)

                    withContext(Dispatchers.IO) {
                        if (replace) {
                            favoriteRepository.deleteAllFavorites()
                            routeRepository.deleteAllRoutes()
                        }
                        exportData.favoriteLocations.forEach { fav ->
                            favoriteRepository.addFavorite(
                                id = fav.id,
                                name = fav.name,
                                position = fav.position,
                                createdAt = fav.createdAt,
                            )
                        }
                        exportData.routes.forEach { routeRepository.insertRoute(it).getOrNull() }
                    }

                    setSpeedUnit(exportData.settings.speedUnit)
                    exportData.speedProfiles.forEach { profile ->
                        when (profile.id) {
                            "walk" -> settingsRepository.setWalkSpeed(profile.speedMetersPerSecond)
                            "run" -> settingsRepository.setRunSpeed(profile.speedMetersPerSecond)
                            "bike" -> settingsRepository.setBikeSpeed(profile.speedMetersPerSecond)
                        }
                    }
                    setWidgetFeatures(exportData.settings.enabledWidgetFeatures.toSet())
                    setJitterIdleRadius(exportData.jitterIdleRadius)
                    setJitterMovingRadius(exportData.jitterMovingRadius)
                    setJitterIntervalSeconds(exportData.jitterIntervalSeconds)
                } catch (e: Exception) {
                    Log.e(TAG, "Import failed", e)
                }
            }
        }

        fun prepareQrChunks() {
            viewModelScope.launch(Dispatchers.IO) {
                try {
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
                    val routes = routeRepository.getRoutes().first()
                    val favorites = favoriteRepository.getFavorites().first()
                    val result =
                        QrChunker.chunk(
                            ExportData(
                                schemaVersion = AppConstants.ExportConstants.SCHEMA_VERSION,
                                exportedAt = System.currentTimeMillis(),
                                settings = settings,
                                speedProfiles = speedProfiles,
                                routes = routes,
                                favoriteLocations = favorites,
                                jitterIdleRadius = state.jitterIdleRadiusMeters,
                                jitterMovingRadius = state.jitterMovingRadiusMeters,
                                jitterIntervalSeconds = state.jitterIntervalSeconds,
                            ),
                        )
                    _qrChunksReady.emit(result)
                } catch (e: Exception) {
                    Log.e(TAG, "QR chunk preparation failed", e)
                }
            }
        }

        fun onChunkScanned(envelope: ChunkEnvelope) {
            if (envelope.k != "lj.s" || envelope.v != 2) {
                Log.e(TAG, "Unknown QR format: k=${envelope.k} v=${envelope.v}")
                return
            }

            val session =
                chunkSessions.getOrPut(envelope.session) {
                    ChunkSession(envelope.total, mutableMapOf())
                }

            val content = decodeChunkContent(envelope.d)
            session.chunks[envelope.chunk] = content

            if (session.chunks.size == session.total) {
                chunkSessions.remove(envelope.session)
                val merged = mergeChunks(session.chunks.values.flatten())
                _qrImportReady.tryEmit(merged)
            }
        }

        fun importSettings(
            exportData: ExportData,
            replace: Boolean = true,
        ) {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (replace) {
                            favoriteRepository.deleteAllFavorites()
                            routeRepository.deleteAllRoutes()
                        }
                        exportData.favoriteLocations.forEach { fav ->
                            favoriteRepository.addFavorite(
                                id = fav.id,
                                name = fav.name,
                                position = fav.position,
                                createdAt = fav.createdAt,
                            )
                        }
                        exportData.routes.forEach { routeRepository.insertRoute(it).getOrNull() }
                    }
                    exportData.speedProfiles.forEach { profile ->
                        when (profile.id) {
                            "walk" -> settingsRepository.setWalkSpeed(profile.speedMetersPerSecond)
                            "run" -> settingsRepository.setRunSpeed(profile.speedMetersPerSecond)
                            "bike" -> settingsRepository.setBikeSpeed(profile.speedMetersPerSecond)
                        }
                    }
                    settingsRepository.setSpeedUnit(exportData.settings.speedUnit)
                    settingsRepository.setWidgetFeatures(exportData.settings.enabledWidgetFeatures)
                    settingsRepository.updateRoamingDefaults(exportData.settings.roamingDefaults)
                    settingsRepository.setRealismBearingHoldIdle(exportData.settings.bearingHoldOnIdle)
                    settingsRepository.setRealismAltitudeEnabled(exportData.settings.altitudeEnabled)
                    settingsRepository.setRealismWarmupEnabled(exportData.settings.warmupEnabled)
                    settingsRepository.setRealismSatelliteExtrasEnabled(exportData.settings.satelliteExtrasEnabled)
                    settingsRepository.setRealismSuspendedMockingEnabled(exportData.settings.suspendedMockingEnabled)
                } catch (e: Exception) {
                    Log.e(TAG, "Import from ExportData failed", e)
                }
            }
        }

        fun importFromGpsJoystick(
            context: Context,
            uri: Uri,
            replace: Boolean,
        ) {
            viewModelScope.launch {
                try {
                    val bytes =
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.readBytes() ?: ByteArray(0)
                        }
                    if (bytes.isEmpty()) {
                        Log.e(TAG, "GPS Joystick import failed: empty file")
                        return@launch
                    }
                    val result = GpsJoystickMigrator.parse(bytes)
                    if (result.isFailure) {
                        Log.e(TAG, "GPS Joystick import failed: ${result.exceptionOrNull()?.message}")
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
                } catch (e: Exception) {
                    Log.e(TAG, "GPS Joystick import failed", e)
                }
            }
        }

        fun importFromYamla(
            context: Context,
            uri: Uri,
            replace: Boolean,
        ) {
            viewModelScope.launch {
                try {
                    val json =
                        withContext(Dispatchers.IO) {
                            context.contentResolver
                                .openInputStream(uri)
                                ?.bufferedReader()
                                ?.readText() ?: ""
                        }
                    if (json.isBlank()) {
                        Log.e(TAG, "YAMLA import failed: empty file")
                        return@launch
                    }
                    val result = YamlaMigrator.parse(json)
                    if (result.isFailure) {
                        Log.e(TAG, "YAMLA import failed: ${result.exceptionOrNull()?.message}")
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
                } catch (e: Exception) {
                    Log.e(TAG, "YAMLA import failed", e)
                }
            }
        }

        private fun decodeChunkContent(encoded: String): List<ChunkContent> {
            val decoded = Base64.decode(encoded, Base64.DEFAULT)
            val decompressed = GZIPInputStream(ByteArrayInputStream(decoded)).readBytes()
            val json = String(decompressed, Charsets.UTF_8)
            return parseChunkContent(json)
        }

        private fun parseChunkContent(json: String): List<ChunkContent> {
            val array = JSONArray(json)
            return (0 until array.length()).map { idx ->
                val obj = array.getJSONObject(idx)
                val type = obj.getString("type")
                when (type) {
                    "settings" -> {
                        val wrapped =
                            JSONObject().apply {
                                put("settings", obj.getJSONObject("payload"))
                                put("schemaVersion", AppConstants.ExportConstants.SCHEMA_VERSION)
                            }
                        val exportData = SettingsExportCodec.parseExportData(wrapped.toString())
                        ChunkContent.Settings(exportData.settings)
                    }

                    "routes" -> {
                        val wrapped =
                            JSONObject().apply {
                                put("routes", obj.getJSONArray("payload"))
                                put("schemaVersion", AppConstants.ExportConstants.SCHEMA_VERSION)
                            }
                        val exportData = SettingsExportCodec.parseExportData(wrapped.toString())
                        ChunkContent.Routes(exportData.routes)
                    }

                    "favorites" -> {
                        val wrapped =
                            JSONObject().apply {
                                put("favoriteLocations", obj.getJSONArray("payload"))
                                put("schemaVersion", AppConstants.ExportConstants.SCHEMA_VERSION)
                            }
                        val exportData = SettingsExportCodec.parseExportData(wrapped.toString())
                        ChunkContent.Favorites(exportData.favoriteLocations)
                    }

                    else -> {
                        error("Unknown chunk type: $type")
                    }
                }
            }
        }

        private fun mergeChunks(allContent: List<ChunkContent>): ExportData = mergeChunkContents(allContent)

        private fun serializeExportData(data: ExportData) = SettingsExportCodec.serializeExportData(data)

        private fun parseExportData(json: String) = SettingsExportCodec.parseExportData(json)
    }
