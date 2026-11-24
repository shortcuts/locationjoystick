package com.locationjoystick.feature.map.impl

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.location.WalkMode
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.MovementState
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteProgress
import com.locationjoystick.core.model.RouteStartConfig
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.isActive
import com.locationjoystick.core.model.isPaused

data class MapUiState(
    val currentPosition: LatLng? = null,
    val mockLocationState: MockLocationState = MockLocationState.IDLE,
    val movement: MovementState = MovementState.Manual(MockMode.JOYSTICK),
    val routes: List<Route> = emptyList(),
    val isUserPanning: Boolean = false,
    val activeSheet: ActiveSheet = ActiveSheet.None,
    val favorites: List<FavoriteLocation> = emptyList(),
    val pendingCameraTarget: LatLng? = null,
    val pendingTapPosition: LatLng? = null,
    val routeTrace: List<LatLng>? = null,
    val walkMode: WalkMode = WalkMode.Idle,
    val isWalkControlsExpanded: Boolean = false,
    val isRouteReplay: Boolean = false,
    val isRouteControlsExpanded: Boolean = false,
    val isDrawLineActive: Boolean = false,
    val pasteFormNonce: Int = 0,
    val showCaptureCoordinatesSheet: Boolean = false,
    val isRoamingControlsExpanded: Boolean = false,
    val roamingPreviewWaypoints: List<LatLng>? = null,
    val isRoamingPreviewLoading: Boolean = false,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val mapFeatureOrder: List<AppFeature> = AppFeature.DEFAULT_MAP_ORDER,
    val enabledMapFeatures: Set<AppFeature> = AppFeature.DEFAULT_MAP_ENABLED,
    val cooldownState: CooldownState = CooldownState.Ready,
    val cooldownEndMs: Long = 0L,
    val favoriteCooldownStates: Map<String, CooldownState> = emptyMap(),
    val homeFavoriteId: String? = null,
    val hideTeleportFeatures: Boolean = false,
    val lastRouteStartConfig: RouteStartConfig? = null,
    val mapTileSource: MapTileSource = MapTileSource.DEFAULT,
    val showRouteJumpButtons: Boolean = AppConstants.ProfileConstants.SHOW_ROUTE_JUMP_BUTTONS_DEFAULT,
    val jitterRadiusMeters: Double = 0.0,
    val debugStatsEnabled: Boolean = false,
    val isRoadRouteFetchInFlight: Boolean = false,
    val routeProgress: RouteProgress? = null,
    val currentSpeedMps: Float? = null,
)

enum class PasteSheetTitle { DEFAULT, GPX }

// Convenience accessors
val MapUiState.walkTarget: LatLng? get() = (walkMode as? WalkMode.Walking)?.target
val MapUiState.walkStart: LatLng? get() = (walkMode as? WalkMode.Walking)?.start
val MapUiState.ephemeralWaypoints: List<LatLng>
    get() = (walkMode as? WalkMode.EphemeralReplay)?.waypoints ?: emptyList()
val MapUiState.isWalkActive: Boolean get() = walkMode !is WalkMode.Idle

val MapUiState.isWalkPaused: Boolean get() = movement.isPaused(MockMode.WALK_TO)
val MapUiState.isRoaming: Boolean get() = movement.isActive(MockMode.ROAMING)
val MapUiState.isRoamingPaused: Boolean get() = movement.isPaused(MockMode.ROAMING)

val MapUiState.isSpoofing: Boolean
    get() = mockLocationState == MockLocationState.RUNNING

val MapUiState.showMapHint: Boolean
    get() = hideTeleportFeatures || isSpoofing

val MapUiState.isRoutePaused: Boolean
    get() = isRouteReplay && mockLocationState == MockLocationState.PAUSED

val MapUiState.showJitterRadiusOverlay: Boolean
    get() = debugStatsEnabled && currentPosition != null && jitterRadiusMeters > 0.0

val MapUiState.showFavoritesSheet: Boolean get() = activeSheet is ActiveSheet.Favorites
val MapUiState.favoriteTarget: FavoriteLocation? get() = (activeSheet as? ActiveSheet.Favorites)?.target
val MapUiState.showRoutesSheet: Boolean get() = activeSheet is ActiveSheet.Routes
val MapUiState.showPasteCoordinatesSheet: Boolean get() = activeSheet is ActiveSheet.Paste
val MapUiState.pasteSheetTitle: PasteSheetTitle
    get() = (activeSheet as? ActiveSheet.Paste)?.title ?: PasteSheetTitle.DEFAULT
val MapUiState.pasteInitialText: String get() = (activeSheet as? ActiveSheet.Paste)?.initialText ?: ""
val MapUiState.pasteInitialRouteName: String get() = (activeSheet as? ActiveSheet.Paste)?.initialRouteName ?: ""
val MapUiState.isPendingTapSheetOpen: Boolean get() = activeSheet is ActiveSheet.PendingTap
val MapUiState.showRoamingSheet: Boolean get() = activeSheet is ActiveSheet.Roaming
val MapUiState.roamingDraft: RoamingDefaults? get() = (activeSheet as? ActiveSheet.Roaming)?.draft
val MapUiState.isRoamingSheetMinimized: Boolean get() = (activeSheet as? ActiveSheet.Roaming)?.minimized == true

// Mirrors the modal sheets rendered in MapScreen; a minimized roaming sheet is not modal.
val MapUiState.hasModalSheetOpen: Boolean
    get() = activeSheet.isModal && (activeSheet !is ActiveSheet.PendingTap || pendingTapPosition != null)
