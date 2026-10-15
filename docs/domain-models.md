# Domain Models

All in `:core:model`. Pure Kotlin — no Android, no Room. Room entities in `:core:database` mirror these, map via extensions.

| Model | Fields |
|-------|--------|
| `LatLng` | `latitude: Double`, `longitude: Double` |
| `Waypoint` | `id: String`, `position: LatLng`, `orderIndex: Int`, `waitSeconds: Int` |
| `Route` | `id: String`, `name: String`, `waypoints: List<Waypoint>`, `isLooping: Boolean`, `routeType: RouteType`, `speedProfileId: String?`, `randomizeTeleportOrder: Boolean`, `plantingRadiusMeters: Double?`, `createdAt: Long`, `updatedAt: Long`, `category: String?` |
| `FavoriteLocation` | `id: String`, `name: String`, `position: LatLng`, `createdAt: Long`, `category: String?` |
| `RouteStartConfig` | `isLooping`, `isReverse`, `isReturnToLocation`, `teleportToStart`, `followRoadsToStart`, `isPlanting`, `teleportBetweenWaypoints`: `Boolean`, `teleportBetweenDelaySeconds: Int` — route-start sheet flags bundle |
| `RouteType` | enum: `STRAIGHT`, `GUIDED`, `TELEPORT` |
| `SavedItemSortMode` | enum: `NAME_ASCENDING`, `NAME_DESCENDING`, `NEWEST_FIRST`, `OLDEST_FIRST` |
| `SpeedProfile` | `id: String`, `name: String`, `speedMetersPerSecond: Double` |
| `RoamingConfig` | `centerPosition: LatLng`, `radiusMeters: Double`, `distanceMeters: Double`, `speedProfileId: String`, `useRoadSnapping: Boolean`, `returnToInitialLocation: Boolean`, `plannedWaypoints: List<LatLng>?`, `kind: RoamingKind`, `plantingStartRadiusMeters: Double`, `plantingEndRadiusMeters: Double`, `plantingInfiniteLoops: Boolean`, `plantingLoopCount: Int`, `plantingSpacingMeters: Int`, `plantingExpandOnly: Boolean` |
| `RoamingDefaults` | Same user-facing fields as `RoamingConfig` minus `centerPosition`/`plannedWaypoints`/`useRoadSnapping`; uses `followRoads` and `kind`; `plantingSpeedProfileId` (default `"bike"`) is independent of walk-around `speedProfileId` (default `"walk"`) |
| `RoamingKind` | enum: `WALK_AROUND`, `PLANTING` |
| `AppSettings` | `activeSpeedProfileId: String`, `enabledSpeedProfileIds: Set<String>`, `joystickStyle: JoystickStyle`, `featureOrder: List<AppFeature>`, `enabledWidgetFeatures: Set<AppFeature>`, `enabledMapFeatures: Set<AppFeature>`, `mapFollowsLocation: Boolean`, `useRoadSnappingByDefault: Boolean`, `speedUnit: SpeedUnit`, `mapTileSource: MapTileSource`, `guidedPath: GuidedPath`, `disabledGeocodingProviders: Set<GeocodingProviderId>`, `roamingDefaults: RoamingDefaults`, `bearingHoldOnIdle: Boolean`, `altitudeEnabled: Boolean`, `warmupEnabled: Boolean`, `satelliteExtrasEnabled: Boolean`, `suspendedMockingEnabled: Boolean`, `hideTeleportFeatures: Boolean`, `hideWidgetOverlay: Boolean`, `hideForegroundNotification: Boolean`, `showRouteJumpButtons: Boolean`, `bypassMockLocationCheck: Boolean`, `realElevationEnabled: Boolean`, `altitudeJitterRadiusMeters: Double`, `plantingRadiusMeters: Double`, `altitudeOverrideButtonEnabled: Boolean`, `debugStatsEnabled: Boolean` |
| `ExportData` | `schemaVersion: Int`, `exportedAt: Long`, `settings: AppSettings`, `speedProfiles: List<SpeedProfile>`, `routes: List<Route>`, `favoriteLocations: List<FavoriteLocation>`, `jitterIdleRadius: Double`, `jitterMovingRadius: Double`, `jitterMaxStepMeters: Double`, `jitterSpeedIdleVariationPct: Int`, `jitterSpeedMovingVariationPct: Int`, `jitterSpeedIdleWobbleProbabilityPct: Int`, `hotLocationsEnabled: Boolean`, `selectedHotLocationIds: Set<String>`, `hotRoutesEnabled: Boolean`, `selectedHotRouteIds: Set<String>`, `routesSortNewestFirst: Boolean`, `favoritesSortNewestFirst: Boolean`, `routesSortMode: SavedItemSortMode`, `favoritesSortMode: SavedItemSortMode`, `homeFavoriteId: String?` |
| `MockMode` | enum: `JOYSTICK`, `ROUTE_REPLAY`, `ROAMING`, `TELEPORT` |
| `MockLocationState` | enum: `IDLE`, `RUNNING`, `PAUSED`, `ERROR` |
| `RouteReplayMode` | enum: `ONE_WAY`, `RETURN_TO_LOCATION`, `LOOP`, `LOOP_REVERSE` |
| `RecentSearch` | `displayName: String`, `lat: Double`, `lon: Double` |
| `AppFeature` | enum (default order shared across widget + map): `MAP_FLOATING`, `JOYSTICK_TOGGLE`, `JOYSTICK_LOCK`, `FAVORITES`, `ROUTES`, `ROAMING`, `SEARCH`, `PASTE_COORDINATES`, `CAPTURE_COORDINATES`, `SPEED_CYCLE`. Each value declares its eligible `FeatureSurface`s (`WIDGET`, `MAP`, or both). `PASTE_COORDINATES` and `ROAMING` are on both WIDGET and MAP; `ROAMING` is in `DEFAULT_MAP_ENABLED` and `DEFAULT_WIDGET_ENABLED`, `PASTE_COORDINATES` is in neither (off by default). `CAPTURE_COORDINATES` is MAP-only and included in `DEFAULT_MAP_ENABLED`. |
| `RouteProgress` | `current: Int`, `total: Int` — 1-based named-stop progress while a route is playing (`label` is `current/total`). |
| `FeatureSurface` | enum: `WIDGET`, `MAP` |
| `JoystickStyle` | enum: `FLOATING`, `FIXED` |
| `SpeedUnit` | enum: `KMH`, `MPH` |
| `GeocodingProviderId` | enum: `NOMINATIM`, `PHOTON` — geocoding services switchable in Settings > General > Place search; lenient `fromName()` returns null for unknown names (see docs/features/map.md) |
| `GuidedPath` | enum: `DEFAULT`, `POKEMON_GO`, `PIKMIN_BLOOM` — chosen onboarding path; `DEFAULT` changes nothing; `showsPlanting` (false on `POKEMON_GO`), `showsTapToWalk` and `showsReconnectAccessory` (false on `PIKMIN_BLOOM`) drive UI visibility only; lenient `fromName()` falls back to `DEFAULT` |
| `MapTileSource` | enum: `OSM`, `AMAP` — carries `tileUrlTemplates`, `minZoom`/`maxZoom`, `clampCamera: Boolean`, `coordinateSystem: MapCoordinateSystem`, `defaultCenter: LatLng`; `DEFAULT = OSM`, lenient `fromName()` (see docs/features/map.md, "Map source") |
| `MapCoordinateSystem` | enum: `WGS84`, `GCJ02` — datum a tile provider renders in; drives the presentation-edge projection in `:core:map` |
| `ThemeMode` | enum: `DARK`, `LIGHT` |
| `AppLanguage` | enum (each carries `languageTag: String?`): `SYSTEM_DEFAULT`, `ENGLISH`, `CHINESE_SIMPLIFIED`, `CHINESE_TRADITIONAL` |
| `GroupRole` | enum: `NONE`, `LEADER`, `FOLLOWER` |
| `GroupState` | `role: GroupRole`, `groupId: String?`, `leaderHost: String?`, `leaderPort: Int?`, `followerModeEnabled: Boolean`, `sharingEnabled: Boolean`, `followLeaderTeleports: Boolean` (default `true`) |
| `SyncPositionUpdate` | `timestamp: Long`, `latitude: Double`, `longitude: Double`, `speedMs: Float`, `bearing: Float`, `seq: Long`, `active: Boolean`, `teleportSeq: Long` (default `0`) |
| `GroupInvite` | `host: String`, `port: Int`, `groupId: String` |
| `GroundCalibration` | `headingDeg: Double`, `lateralPxPerM: Double`, `forwardPxPerM: Double`, `perspective: Double`, `feetX: Double`, `feetY: Double` — fitted tap-to-walk camera terms in reference pixels (1080x2340); see docs/features/tap-to-walk.md, "Ground map model" |
| `GroundOffset` | `eastMeters: Double`, `northMeters: Double` — metres from the player's feet, north up |
| `GroundMap` | class (not data): `GroundMap(cal, screenWidth, screenHeight)`; `pixelToGround(x, y): GroundOffset?`, `groundToPixel(east, north)`. Pure Kotlin perspective pixel/ground mapping |
| `GrayFrame` | `pixels: FloatArray` — row-major gray 0..255 frame fed to `FeetDetector` |
| `FeetPixel` | `x: Double`, `y: Double` device pixels; `toReference(width, height)` → reference-frame pair |
| `FeetDetector` | object: `detect(legs, width, height): FeetPixel?` — pure-Kotlin feet pixel from walked-leg frames; `legGradient(frames: List<GrayImage>): FloatArray?` (null under 3 frames) and `detectFromGradients(grads, width, height): FeetPixel?` are the incremental halves of `detect` (one gradient per leg kept instead of every frame); see docs/features/tap-to-walk.md, "Feet detection" |
| `GrayImage` | `width: Int`, `height: Int`, `pixels: ByteArray` — row-major 8-bit gray frame fed to `FrameFlow` |
| `WalkLeg` | `frames: List<GrayImage>`, `moving: List<Boolean>`, `offsets: List<GroundOffset>` — one walked leg; offsets are the mock position of each frame in metres |
| `BandFlow` | `fx`, `fy`, `response`, `cx`, `cy` — content shift of one band between two frames and its centre, device pixels |
| `LegMove` | `d: GroundOffset`, `bands: List<BandFlow>` — a known move and the flow of every band it caused |
| `FlowResult` | `fx`, `fy`, `response` — output of `PhaseCorrelation.correlate` |
| `GroundFit` | `calibration: GroundCalibration`, `medianAbsPx: Double`, `flowCount: Int` — output of `GroundMapFit.fit` |
| `PhaseCorrelation` | object: `correlate(a, b, width, height): FlowResult` (also `spectrum` / `correlate(Spectrum, Spectrum)` to reuse a frame's transform) — pure-Kotlin windowed phase correlation, `cv2.phaseCorrelate` equivalent |
| `FrameFlow` | object: `cleanLeg(frames, feetX, feetY)`, `bandMoves(legs, feetX, feetY, ks)` — static-content cleanup and per-band flow; see docs/features/tap-to-walk.md, "Frame flow and ground fit" |
| `StoredGroundCalibration` | `calibration: GroundCalibration`, `screenWidth: Int`, `screenHeight: Int`, `calibratedAtMs: Long`, `flowCount: Int`, `medianAbsPx: Double`; `matches(width, height)` — latest calibration walk result, stored per device (DataStore key `tap_to_walk_ground_calibration`, JSON `v` 1), not part of `ExportData`; see docs/features/tap-to-walk.md, "Calibration walk" |
| `CalibrationWalkMath` | object: `LEG_METERS` (40), `BEARINGS_DEG` (0,180,90,270,45,225,135,315), `METERS_PER_DEGREE` (111320), `commandedTarget`, `alongTrackOffset`, `movingFlags` — calibration walk geometry, bench parity |
| `CapturedLeg` | `bearingDeg: Double`, `timesS: DoubleArray`, `positions: List<LatLng>`, `frames: () -> List<GrayImage>` — one captured calibration leg; frames are loaded lazily, one leg at a time |
| `GroundCalibrationSolver` / `SolveResult` | object: `solve(legs, width, height, onStep): SolveResult` — feet pixel, band flows and fit over the captured legs; result `Ok(fit, feet)`, `FeetNotFound`, `TooFewFlows` or `ImplausibleFit` (a scale under 1 reference px per metre) |
| `GroundMapFit` | object: `fit(moves, feetX, feetY, width, height): GroundFit?` — least-squares perspective ground map fit; `null` under 40 usable flows |

## Mapping

`:core:database` Room entities mirror domain models. Conversion via extensions in repo layer — never ViewModels.
