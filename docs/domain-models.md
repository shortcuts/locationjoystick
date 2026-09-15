# Domain Models

All in `:core:model`. Pure Kotlin — no Android, no Room. Room entities in `:core:database` mirror these, map via extensions.

| Model | Fields |
|-------|--------|
| `LatLng` | `latitude: Double`, `longitude: Double` |
| `Waypoint` | `id: String`, `position: LatLng`, `orderIndex: Int`, `waitSeconds: Int` |
| `Route` | `id: String`, `name: String`, `waypoints: List<Waypoint>`, `isLooping: Boolean`, `routeType: RouteType`, `speedProfileId: String?`, `randomizeTeleportOrder: Boolean`, `createdAt: Long`, `updatedAt: Long` |
| `FavoriteLocation` | `id: String`, `name: String`, `position: LatLng`, `createdAt: Long`, `category: String?` |
| `RouteType` | enum: `STRAIGHT`, `GUIDED`, `TELEPORT` |
| `SavedItemSortMode` | enum: `NAME_ASCENDING`, `NAME_DESCENDING`, `NEWEST_FIRST`, `OLDEST_FIRST` |
| `SpeedProfile` | `id: String`, `name: String`, `speedMetersPerSecond: Double` |
| `RoamingConfig` | `centerPosition: LatLng`, `radiusMeters: Double`, `distanceMeters: Double`, `speedProfileId: String`, `useRoadSnapping: Boolean`, `returnToInitialLocation: Boolean`, `plannedWaypoints: List<LatLng>?`, `kind: RoamingKind`, `plantingStartRadiusMeters: Double`, `plantingEndRadiusMeters: Double`, `plantingInfiniteLoops: Boolean`, `plantingLoopCount: Int` |
| `RoamingDefaults` | Same user-facing fields as `RoamingConfig` minus `centerPosition`/`plannedWaypoints`/`useRoadSnapping`; uses `followRoads` and `kind`; `plantingSpeedProfileId` (default `"bike"`) is independent of walk-around `speedProfileId` (default `"walk"`) |
| `RoamingKind` | enum: `WALK_AROUND`, `PLANTING` |
| `AppSettings` | `activeSpeedProfileId: String`, `enabledSpeedProfileIds: Set<String>`, `joystickStyle: JoystickStyle`, `featureOrder: List<AppFeature>`, `enabledWidgetFeatures: Set<AppFeature>`, `enabledMapFeatures: Set<AppFeature>`, `mapFollowsLocation: Boolean`, `useRoadSnappingByDefault: Boolean`, `speedUnit: SpeedUnit`, `roamingDefaults: RoamingDefaults`, `bearingHoldOnIdle: Boolean`, `altitudeEnabled: Boolean`, `warmupEnabled: Boolean`, `satelliteExtrasEnabled: Boolean`, `suspendedMockingEnabled: Boolean`, `hideTeleportFeatures: Boolean`, `hideWidgetOverlay: Boolean`, `hideForegroundNotification: Boolean`, `showRouteJumpButtons: Boolean`, `bypassMockLocationCheck: Boolean`, `realElevationEnabled: Boolean`, `altitudeJitterRadiusMeters: Double`, `altitudeOverrideButtonEnabled: Boolean`, `debugStatsEnabled: Boolean` |
| `ExportData` | `schemaVersion: Int`, `exportedAt: Long`, `settings: AppSettings`, `speedProfiles: List<SpeedProfile>`, `routes: List<Route>`, `favoriteLocations: List<FavoriteLocation>`, `jitterIdleRadius: Double`, `jitterMovingRadius: Double`, `jitterMaxStepMeters: Double`, `jitterSpeedIdleVariationPct: Int`, `jitterSpeedMovingVariationPct: Int`, `jitterSpeedIdleWobbleProbabilityPct: Int`, `hotLocationsEnabled: Boolean`, `selectedHotLocationIds: Set<String>`, `hotRoutesEnabled: Boolean`, `selectedHotRouteIds: Set<String>`, `routesSortNewestFirst: Boolean`, `favoritesSortNewestFirst: Boolean`, `routesSortMode: SavedItemSortMode`, `favoritesSortMode: SavedItemSortMode` |
| `MockMode` | enum: `JOYSTICK`, `ROUTE_REPLAY`, `ROAMING`, `TELEPORT` |
| `MockLocationState` | enum: `IDLE`, `RUNNING`, `PAUSED`, `ERROR` |
| `RouteReplayMode` | enum: `ONE_WAY`, `RETURN_TO_LOCATION`, `LOOP`, `LOOP_REVERSE` |
| `RecentSearch` | `displayName: String`, `lat: Double`, `lon: Double` |
| `AppFeature` | enum (default order shared across widget + map): `MAP_FLOATING`, `JOYSTICK_TOGGLE`, `JOYSTICK_LOCK`, `FAVORITES`, `ROUTES`, `ROAMING`, `SEARCH`, `PASTE_COORDINATES`, `CAPTURE_COORDINATES`, `SPEED_CYCLE`. Each value declares its eligible `FeatureSurface`s (`WIDGET`, `MAP`, or both). `PASTE_COORDINATES` and `ROAMING` are on both WIDGET and MAP and included in `DEFAULT_MAP_ENABLED` and `DEFAULT_WIDGET_ENABLED`. `CAPTURE_COORDINATES` is MAP-only and included in `DEFAULT_MAP_ENABLED`. |
| `RouteProgress` | `current: Int`, `total: Int` — 1-based named-stop progress while a route is playing (`label` is `current/total`). |
| `FeatureSurface` | enum: `WIDGET`, `MAP` |
| `JoystickStyle` | enum: `FLOATING`, `FIXED` |
| `SpeedUnit` | enum: `KMH`, `MPH` |
| `ThemeMode` | enum: `DARK`, `LIGHT` |
| `GroupRole` | enum: `NONE`, `LEADER`, `FOLLOWER` |
| `GroupState` | `role: GroupRole`, `groupId: String?`, `leaderHost: String?`, `leaderPort: Int?`, `followerModeEnabled: Boolean`, `sharingEnabled: Boolean` |
| `SyncPositionUpdate` | `timestamp: Long`, `latitude: Double`, `longitude: Double`, `speedMs: Float`, `bearing: Float`, `seq: Long`, `active: Boolean` |
| `GroupInvite` | `host: String`, `port: Int`, `groupId: String` |

## Mapping

`:core:database` Room entities mirror domain models. Conversion via extensions in repo layer — never ViewModels.
