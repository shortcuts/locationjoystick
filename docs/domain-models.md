# Domain Models

All in `:core:model`. Pure Kotlin — no Android, no Room. Room entities in `:core:database` mirror these, map via extensions.

| Model | Fields |
|-------|--------|
| `LatLng` | `latitude: Double`, `longitude: Double` |
| `Waypoint` | `id: String`, `position: LatLng`, `orderIndex: Int` |
| `Route` | `id: String`, `name: String`, `waypoints: List<Waypoint>`, `isLooping: Boolean`, `routeType: RouteType`, `createdAt: Long`, `updatedAt: Long` |
| `FavoriteLocation` | `id: String`, `name: String`, `position: LatLng`, `createdAt: Long` |
| `RouteType` | enum: `STRAIGHT`, `GUIDED` |
| `SpeedProfile` | `id: String`, `name: String`, `speedMetersPerSecond: Double` |
| `RoamingConfig` | `centerPosition: LatLng`, `radiusMeters: Double`, `durationSeconds: Long`, `useRoadSnapping: Boolean` |
| `RoamingDefaults` | `radiusMeters: Double`, `distanceMeters: Double`, `speedProfileId: String`, `followRoads: Boolean`, `returnToInitialLocation: Boolean` |
| `AppSettings` | `activeSpeedProfileId: String`, `joystickStyle: JoystickStyle`, `featureOrder: List<AppFeature>`, `enabledWidgetFeatures: Set<AppFeature>`, `enabledMapFeatures: Set<AppFeature>`, `mapFollowsLocation: Boolean`, `useRoadSnappingByDefault: Boolean`, `speedUnit: SpeedUnit`, `roamingDefaults: RoamingDefaults`, `bearingHoldOnIdle: Boolean`, `altitudeEnabled: Boolean`, `warmupEnabled: Boolean`, `satelliteExtrasEnabled: Boolean`, `suspendedMockingEnabled: Boolean`, `pedometerMockingEnabled: Boolean` |
| `ExportData` | `schemaVersion: Int`, `exportedAt: Long`, `settings: AppSettings`, `speedProfiles: List<SpeedProfile>`, `routes: List<Route>`, `favoriteLocations: List<FavoriteLocation>`, `jitterIdleRadius: Double`, `jitterMovingRadius: Double`, `jitterIntervalSeconds: Int`, `jitterIdleIntervalSeconds: Int`, `jitterSpeedIdleVariationPct: Int`, `jitterSpeedMovingVariationPct: Int`, `hotLocationsEnabled: Boolean`, `selectedHotLocationIds: Set<String>`, `hotRoutesEnabled: Boolean`, `selectedHotRouteIds: Set<String>`, `routesSortNewestFirst: Boolean`, `favoritesSortNewestFirst: Boolean` |
| `MockMode` | enum: `JOYSTICK`, `ROUTE_REPLAY`, `ROAMING`, `TELEPORT` |
| `MockLocationState` | enum: `IDLE`, `RUNNING`, `PAUSED`, `ERROR` |
| `RouteReplayMode` | enum: `ONE_WAY`, `RETURN_TO_LOCATION`, `LOOP`, `LOOP_REVERSE` |
| `RecentSearch` | `displayName: String`, `lat: Double`, `lon: Double` |
| `AppFeature` | enum (default order shared across widget + map): `MAP_FLOATING`, `JOYSTICK_TOGGLE`, `JOYSTICK_LOCK`, `FAVORITES`, `ROUTES`, `ROAMING`, `SEARCH`, `SPEED_CYCLE`. Each value declares its eligible `FeatureSurface`s (`WIDGET`, `MAP`, or both). |
| `FeatureSurface` | enum: `WIDGET`, `MAP` |
| `JoystickStyle` | enum: `FLOATING`, `FIXED` |
| `SpeedUnit` | enum: `KMH`, `MPH` |
| `ThemeMode` | enum: `DARK`, `LIGHT` |
| `GroupRole` | enum: `NONE`, `LEADER`, `FOLLOWER` |
| `GroupState` | `role: GroupRole`, `groupId: String?`, `leaderHost: String?`, `leaderPort: Int?`, `followerModeEnabled: Boolean`, `sharingEnabled: Boolean` |
| `SyncPositionUpdate` | `timestamp: Long`, `latitude: Double`, `longitude: Double`, `speedMs: Float`, `bearing: Float`, `seq: Long` |
| `GroupInvite` | `host: String`, `port: Int`, `groupId: String` |

## Mapping

`:core:database` Room entities mirror domain models. Conversion via extensions in repo layer — never ViewModels.