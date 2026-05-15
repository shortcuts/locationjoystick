package com.locationjoystick.feature.settings.impl

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.AppSettings
import com.locationjoystick.core.model.ExportData
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
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

        private data class RepoState(
            val walkSpeed: Double,
            val runSpeed: Double,
            val bikeSpeed: Double,
            val speedUnit: SpeedUnit,
            val widgetFeatures: Set<WidgetFeature>,
            val rememberLastLocation: Boolean,
            val jitterIdleRadius: Double,
            val jitterMovingRadius: Double,
            val jitterIntervalSeconds: Int,
        )

        private data class DraftState(
            val walkSpeed: Double? = null,
            val runSpeed: Double? = null,
            val bikeSpeed: Double? = null,
            val speedUnit: SpeedUnit? = null,
            val widgetFeatures: Set<WidgetFeature>? = null,
            val rememberLastLocation: Boolean? = null,
            val jitterIdleRadius: Double? = null,
            val jitterMovingRadius: Double? = null,
            val jitterIntervalSeconds: Int? = null,
        )

        private val draftWalkSpeedFlow = MutableStateFlow<Double?>(null)
        private val draftRunSpeedFlow = MutableStateFlow<Double?>(null)
        private val draftBikeSpeedFlow = MutableStateFlow<Double?>(null)
        private val draftSpeedUnitFlow = MutableStateFlow<SpeedUnit?>(null)
        private val draftWidgetFeaturesFlow = MutableStateFlow<Set<WidgetFeature>?>(null)
        private val draftRememberLastLocationFlow = MutableStateFlow<Boolean?>(null)
        private val draftJitterIdleRadiusFlow = MutableStateFlow<Double?>(null)
        private val draftJitterMovingRadiusFlow = MutableStateFlow<Double?>(null)
        private val draftJitterIntervalSecondsFlow = MutableStateFlow<Int?>(null)

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
            ) { speeds, settings, jitter ->
                RepoState(
                    walkSpeed = speeds.first,
                    runSpeed = speeds.second,
                    bikeSpeed = speeds.third,
                    speedUnit = settings.first,
                    widgetFeatures = settings.second.toSet(),
                    rememberLastLocation = settings.third,
                    jitterIdleRadius = jitter.first,
                    jitterMovingRadius = jitter.second,
                    jitterIntervalSeconds = jitter.third,
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
            ) { speeds, settings, jitter ->
                DraftState(
                    walkSpeed = speeds.first,
                    runSpeed = speeds.second,
                    bikeSpeed = speeds.third,
                    speedUnit = settings.first,
                    widgetFeatures = settings.second,
                    rememberLastLocation = settings.third,
                    jitterIdleRadius = jitter.first,
                    jitterMovingRadius = jitter.second,
                    jitterIntervalSeconds = jitter.third,
                )
            }

        val uiState: StateFlow<SettingsUiState> =
            combine(
                repoStateFlow,
                draftStateFlow,
            ) { repoState, draftState ->
                val isDirty =
                    draftState.walkSpeed != null || draftState.runSpeed != null ||
                        draftState.bikeSpeed != null || draftState.speedUnit != null ||
                        draftState.widgetFeatures != null || draftState.rememberLastLocation != null ||
                        draftState.jitterIdleRadius != null || draftState.jitterMovingRadius != null ||
                        draftState.jitterIntervalSeconds != null
                SettingsUiState(
                    isLoading = false,
                    walkSpeed = draftState.walkSpeed ?: repoState.walkSpeed,
                    runSpeed = draftState.runSpeed ?: repoState.runSpeed,
                    bikeSpeed = draftState.bikeSpeed ?: repoState.bikeSpeed,
                    speedUnit = draftState.speedUnit ?: repoState.speedUnit,
                    enabledWidgetFeatures = draftState.widgetFeatures ?: repoState.widgetFeatures,
                    rememberLastLocation = draftState.rememberLastLocation ?: repoState.rememberLastLocation,
                    jitterIdleRadiusMeters = draftState.jitterIdleRadius ?: repoState.jitterIdleRadius,
                    jitterMovingRadiusMeters = draftState.jitterMovingRadius ?: repoState.jitterMovingRadius,
                    jitterIntervalSeconds = draftState.jitterIntervalSeconds ?: repoState.jitterIntervalSeconds,
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

        fun setJitterIdleRadius(meters: Double) {
            draftJitterIdleRadiusFlow.value = meters
        }

        fun setJitterMovingRadius(meters: Double) {
            draftJitterMovingRadiusFlow.value = meters
        }

        fun setJitterIntervalSeconds(seconds: Int) {
            draftJitterIntervalSecondsFlow.value = seconds
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
            }
        }

        fun discardChanges() {
            draftWalkSpeedFlow.value = null
            draftRunSpeedFlow.value = null
            draftBikeSpeedFlow.value = null
            draftSpeedUnitFlow.value = null
            draftWidgetFeaturesFlow.value = null
            draftRememberLastLocationFlow.value = null
            draftJitterIdleRadiusFlow.value = null
            draftJitterMovingRadiusFlow.value = null
            draftJitterIntervalSecondsFlow.value = null
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
                    val settings = AppSettings(speedUnit = state.speedUnit, enabledWidgetFeatures = state.enabledWidgetFeatures.toList())
                    val routes = routeRepository.getRoutes().first()
                    val favorites = favoriteRepository.getFavorites().first()

                    val exportData =
                        ExportData(
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
                            "walk" -> setWalkSpeed(profile.speedMetersPerSecond)
                            "run" -> setRunSpeed(profile.speedMetersPerSecond)
                            "bike" -> setBikeSpeed(profile.speedMetersPerSecond)
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

        private fun serializeExportData(data: ExportData): String {
            val root = JSONObject()
            root.put("schemaVersion", data.schemaVersion)
            root.put("exportedAt", data.exportedAt)

            val settingsObj = JSONObject()
            settingsObj.put("speedUnit", data.settings.speedUnit.name)
            settingsObj.put("enabledWidgetFeatures", JSONArray(data.settings.enabledWidgetFeatures.map { it.name }))
            root.put("settings", settingsObj)

            val profilesArray = JSONArray()
            data.speedProfiles.forEach { profile ->
                val obj = JSONObject()
                obj.put("id", profile.id)
                obj.put("name", profile.name)
                obj.put("speedMetersPerSecond", profile.speedMetersPerSecond)
                profilesArray.put(obj)
            }
            root.put("speedProfiles", profilesArray)

            val routesArray = JSONArray()
            data.routes.forEach { route ->
                val obj = JSONObject()
                obj.put("id", route.id)
                obj.put("name", route.name)
                obj.put("isLooping", route.isLooping)
                obj.put("createdAt", route.createdAt)
                val wpArray = JSONArray()
                route.waypoints.forEach { wp ->
                    val wpObj = JSONObject()
                    wpObj.put("id", wp.id)
                    wpObj.put("lat", wp.position.latitude)
                    wpObj.put("lon", wp.position.longitude)
                    wpObj.put("orderIndex", wp.orderIndex)
                    wpArray.put(wpObj)
                }
                obj.put("waypoints", wpArray)
                routesArray.put(obj)
            }
            root.put("routes", routesArray)

            val favsArray = JSONArray()
            data.favoriteLocations.forEach { fav ->
                val obj = JSONObject()
                obj.put("id", fav.id)
                obj.put("name", fav.name)
                obj.put("lat", fav.position.latitude)
                obj.put("lon", fav.position.longitude)
                obj.put("createdAt", fav.createdAt)
                favsArray.put(obj)
            }
            root.put("favoriteLocations", favsArray)

            root.put("jitterIdleRadius", data.jitterIdleRadius)
            root.put("jitterMovingRadius", data.jitterMovingRadius)
            root.put("jitterIntervalSeconds", data.jitterIntervalSeconds)

            return root.toString(2)
        }

        private fun parseExportData(json: String): ExportData {
            val root = JSONObject(json)
            val schemaVersion = root.optInt("schemaVersion", 1)
            if (schemaVersion != 1) throw IllegalArgumentException("Unsupported schema version: $schemaVersion")

            val settingsObj = root.optJSONObject("settings") ?: JSONObject()
            val speedUnit =
                try {
                    SpeedUnit.valueOf(settingsObj.optString("speedUnit", "KMH"))
                } catch (e: IllegalArgumentException) {
                    SpeedUnit.KMH
                }
            val enabledFeatures = mutableListOf<WidgetFeature>()
            settingsObj.optJSONArray("enabledWidgetFeatures")?.let { arr ->
                for (i in 0 until arr.length()) {
                    try {
                        enabledFeatures.add(WidgetFeature.valueOf(arr.getString(i)))
                    } catch (e: IllegalArgumentException) {
                        // skip
                    }
                }
            }
            val settings = AppSettings(speedUnit = speedUnit, enabledWidgetFeatures = enabledFeatures)

            val speedProfiles = mutableListOf<SpeedProfile>()
            root.optJSONArray("speedProfiles")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    speedProfiles.add(
                        SpeedProfile(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            speedMetersPerSecond = obj.getDouble("speedMetersPerSecond"),
                        ),
                    )
                }
            }

            val routes = mutableListOf<Route>()
            root.optJSONArray("routes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val waypoints = mutableListOf<com.locationjoystick.core.model.Waypoint>()
                    obj.optJSONArray("waypoints")?.let { wpArr ->
                        for (j in 0 until wpArr.length()) {
                            val wpObj = wpArr.getJSONObject(j)
                            waypoints.add(
                                com.locationjoystick.core.model.Waypoint(
                                    id = wpObj.getString("id"),
                                    position =
                                        com.locationjoystick.core.model.LatLng(
                                            latitude = wpObj.getDouble("lat"),
                                            longitude = wpObj.getDouble("lon"),
                                        ),
                                    orderIndex = wpObj.getInt("orderIndex"),
                                ),
                            )
                        }
                    }
                    routes.add(
                        Route(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            waypoints = waypoints,
                            isLooping = obj.optBoolean("isLooping", false),
                            createdAt = obj.optLong("createdAt", 0),
                        ),
                    )
                }
            }

            val favorites = mutableListOf<FavoriteLocation>()
            root.optJSONArray("favoriteLocations")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    favorites.add(
                        FavoriteLocation(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            position =
                                com.locationjoystick.core.model.LatLng(
                                    latitude = obj.getDouble("lat"),
                                    longitude = obj.getDouble("lon"),
                                ),
                            createdAt = obj.optLong("createdAt", 0),
                        ),
                    )
                }
            }

            return ExportData(
                schemaVersion = schemaVersion,
                exportedAt = root.optLong("exportedAt", 0),
                settings = settings,
                speedProfiles = speedProfiles,
                routes = routes,
                favoriteLocations = favorites,
                jitterIdleRadius = root.optDouble("jitterIdleRadius", 0.0),
                jitterMovingRadius = root.optDouble("jitterMovingRadius", 10.0),
                jitterIntervalSeconds = root.optInt("jitterIntervalSeconds", 5),
            )
        }
    }
