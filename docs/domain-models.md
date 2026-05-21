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
| `AppSettings` | `activeSpeedProfileId: String`, `joystickStyle: JoystickStyle`, `enabledWidgetFeatures: List<WidgetFeature>`, `mapFollowsLocation: Boolean`, `useRoadSnappingByDefault: Boolean`, `speedUnit: SpeedUnit`, `roamingDefaults: RoamingDefaults`, `bearingHoldOnIdle: Boolean`, `altitudeEnabled: Boolean`, `warmupEnabled: Boolean`, `satelliteExtrasEnabled: Boolean`, `suspendedMockingEnabled: Boolean` |
| `ExportData` | `schemaVersion: Int`, `exportedAt: Long`, `settings: AppSettings`, `speedProfiles: List<SpeedProfile>`, `routes: List<Route>`, `favoriteLocations: List<FavoriteLocation>`, `jitterIdleRadius: Double`, `jitterMovingRadius: Double`, `jitterIntervalSeconds: Int` |
| `MockMode` | enum: `JOYSTICK`, `ROUTE_REPLAY`, `ROAMING`, `TELEPORT` |
| `MockLocationState` | enum: `IDLE`, `RUNNING`, `PAUSED`, `ERROR` |
| `WidgetFeature` | enum: `JOYSTICK_TOGGLE`, `JOYSTICK_LOCK`, `ROUTES_FLOATING`, `FAVORITES_FLOATING`, `SPEED_CYCLE`, `MAP_FLOATING` |
| `JoystickStyle` | enum: `FLOATING`, `FIXED` |
| `SpeedUnit` | enum: `KMH`, `MPH` |

## Mapping

`:core:database` Room entities mirror domain models. Conversion via extensions in repo layer — never ViewModels.