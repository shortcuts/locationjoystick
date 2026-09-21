package com.locationjoystick.core.common.constants

object AppConstants {
    object LocationConstants {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val METERS_PER_LATITUDE_DEGREE = 111_320.0
        const val UPDATE_INTERVAL_MS = 1000L
        const val LOCATION_ACCURACY_FINE = 3.0f
        const val WALK_ARRIVAL_THRESHOLD_METERS = 1.0
        const val DEFAULT_REPLAY_SPEED_MS = ProfileConstants.WALK_SPEED_MPS
        const val DEGREES_IN_CIRCLE = 360.0
        const val CARDINAL_SNAP_STEP_DEGREES = 45.0
        const val LAST_LOCATION_PERSIST_INTERVAL_MS = 5000L
    }

    object ProfileConstants {
        const val PROFILE_ID_SLOW_WALK = "slow_walk"
        const val PROFILE_ID_WALK = "walk"
        const val PROFILE_ID_RUN = "run"
        const val PROFILE_ID_BIKE = "bike"
        const val PROFILE_ID_DRIVE = "drive"
        const val SLOW_WALK_SPEED_MPS = 0.3
        const val WALK_SPEED_MPS = 0.5556
        const val RUN_SPEED_MPS = 2.2222
        const val BIKE_SPEED_MPS = 4.1667
        const val DRIVE_SPEED_MPS = 15.0
        const val DEFAULT_ACTIVE_PROFILE_ID = PROFILE_ID_WALK
        const val MIN_SPEED_MS = 0.01
        const val MAX_SPEED_MS = 15.0
        const val ANTI_CHEAT_WARNING_THRESHOLD_MS = 8.0
        const val DEFAULT_SPEED_UNIT = "KMH"
        const val SHOW_ROUTE_JUMP_BUTTONS_DEFAULT = false
        val DEFAULT_ENABLED_SPEED_PROFILE_IDS = setOf(PROFILE_ID_WALK, PROFILE_ID_RUN, PROFILE_ID_BIKE)
    }

    object JitterConstants {
        const val DEFAULT_IDLE_RADIUS_METERS = 0.8
        const val DEFAULT_MOVING_RADIUS_METERS = 0.3
        const val MAX_RADIUS_METERS = 50.0
        const val LONGITUDINAL_JITTER_FRACTION = 0.2
        const val DEFAULT_STEP_METERS_PER_TICK = 1.0
        const val MIN_STEP_METERS_PER_TICK = 0.1
        const val MAX_STEP_METERS_PER_TICK = 5.0
        const val ACCURACY_MIN = 2.0f
        const val ACCURACY_MAX = 5.0f
        const val ACCURACY_PERTURBATION_RANGE = 3.0
        const val SPEED_IDLE_VARIATION_PCT_DEFAULT = 5
        const val SPEED_MOVING_VARIATION_PCT_DEFAULT = 5
        const val SPEED_VARIATION_PCT_MIN = 0
        const val SPEED_VARIATION_PCT_MAX = 50

        /** Real idle GPS noise is well under 0.1 m/s — independent of the active speed profile. */
        const val IDLE_SPEED_WOBBLE_MAX_MPS = 0.1

        /** Matches the previously hardcoded IDLE_SPEED_WOBBLE_PROBABILITY (0.15) default — now
         *  user-configurable via jitterSpeedIdleWobbleProbabilityPct (issue #59). */
        const val SPEED_IDLE_WOBBLE_PROBABILITY_PCT_DEFAULT = 15
    }

    object RealismConstants {
        const val DEFAULT_ALTITUDE_METERS = 35.0

        /** Real phone altitude readings jump several meters tick-to-tick, not sub-meter smooth. */
        const val ALTITUDE_SIGMA_METERS = 1.5
        const val ALTITUDE_CLAMP_RADIUS_METERS = 25.0
        const val VERTICAL_ACCURACY_METERS = 4.0f

        /** Real bearing accuracy widens toward this near a full stop, where heading is undefined. */
        const val BEARING_ACCURACY_STOPPED_DEGREES = 180.0f
        const val BEARING_ACCURACY_MOVING_MIN_DEGREES = 5.0f
        const val BEARING_ACCURACY_MOVING_MAX_DEGREES = 50.0f
        const val BEARING_ACCURACY_MOVING_NOISE_DEGREES = 10.0f

        /** Speed at which moving bearing accuracy reaches its tightest (MIN) value. */
        const val BEARING_ACCURACY_REFERENCE_SPEED_MPS = ProfileConstants.RUN_SPEED_MPS
        const val BEARING_NOISE_DEGREES = 5.0f
        const val SPEED_ACCURACY_MPS = 0.3f

        /** Modern multi-GNSS (GPS+GLONASS+Galileo+BeiDou) phones typically see 15-30 visible. */
        const val SATELLITES_MIN = 15
        const val SATELLITES_MAX = 30
        const val USED_IN_FIX_MIN = 10
        const val USED_IN_FIX_MAX = 20
        const val SATELLITE_UPDATE_INTERVAL_MS = 5_000L
        const val WARMUP_DURATION_SECONDS = 30
        const val WARMUP_INITIAL_ACCURACY_METERS = 50.0f
        const val WARMUP_ENABLED_DEFAULT = false
        const val SUSPENDED_MOCKING_ENABLED_DEFAULT = false
        const val SUSPENDED_PUSH_DURATION_MS = 8_000L
        const val SUSPENDED_PAUSE_DURATION_MS = 2_000L
        const val SUSPENDED_PAUSE_JITTER_MS = 800L
        const val BEARING_HOLD_ON_IDLE_DEFAULT = true
        const val ALTITUDE_ENABLED_DEFAULT = true
        const val SATELLITE_EXTRAS_ENABLED_DEFAULT = true
        const val REAL_ELEVATION_ENABLED_DEFAULT = true
        const val ELEVATION_FETCH_INTERVAL_MS = 60_000L

        /** Open-Meteo's DEM elevation is whole-meter; this keeps the convergence anchor off round numbers. */
        const val ELEVATION_FRACTIONAL_JITTER_METERS = 0.49

        /** ponytail: flat rate, tune or make distance/time-proportional if this ever proves too slow/fast. */
        const val ALTITUDE_TARGET_STEP_METERS_PER_TICK = 0.5
    }

    /** Shared geometry values for both roaming and route planting circles. */
    object PlantingConstants {
        const val MAX_RADIUS_METERS = 200.0
        const val CHORD_METERS = 8.0
    }

    object RoamingConstants {
        const val DEFAULT_RADIUS_METERS = 500.0
        const val OSRM_PROFILE_FOOT = "foot"
        const val OSRM_PROFILE_BIKE = "bike"
        const val OSRM_PROFILE_DRIVING = "driving"
        const val DEFAULT_DISTANCE_METERS = 1_000.0
        const val DEFAULT_FOLLOW_ROADS = true
        const val DEFAULT_RETURN_TO_START = true
        const val RADIUS_MAX_METERS = 100_000.0
        const val DISTANCE_MIN_METERS = 50.0
        const val DISTANCE_MAX_METERS = 50_000.0
        const val WAYPOINTS_PER_1000M = 30
        const val MAX_OSRM_PLANNING_CALLS = 50
        const val PLANTING_START_RADIUS_METERS = 5.0
        const val PLANTING_END_RADIUS_METERS = 39.0
        const val ROAMING_MIN_RADIUS_METERS = 1.0
        val PLANTING_MAX_RADIUS_METERS = PlantingConstants.MAX_RADIUS_METERS
        const val PLANTING_PITCH_METERS = 5.0
        val PLANTING_CHORD_METERS = PlantingConstants.CHORD_METERS
        const val PLANTING_MIN_REVOLUTIONS = 2
        const val PLANTING_DEFAULT_LOOP_COUNT = 1
        const val PLANTING_MAX_LOOP_COUNT = 99
        const val PLANTING_INFINITE_LOOPS_DEFAULT = true
        const val PLANTING_DEFAULT_SPEED_PROFILE_ID = ProfileConstants.PROFILE_ID_BIKE
    }

    object OsrmConstants {
        /** OSRM demo server — single car-ish graph regardless of the profile in the URL. */
        const val BASE_URL = "https://router.project-osrm.org/"

        /** FOSSGIS OSRM instances — separate real foot/bike/car graphs at `/routed-{foot|bike|car}`. */
        const val FOSSGIS_BASE_URL = "https://routing.openstreetmap.de"
        const val OVERVIEW = "full"
        const val GEOMETRIES = "geojson"

        /** Wait before ladder slot N+1 after slot N fails (see OsrmClient ladder). Size = max slots − 1. */
        val LADDER_BACKOFF_MS: List<Long> = listOf(200L, 400L, 700L, 1_000L, 1_500L)

        /** Random jitter (±) applied to [LADDER_BACKOFF_MS] to avoid thundering herd against shared servers. */
        const val RETRY_JITTER_MS = 100L

        /** Wait for a 429 (rate limited) response with no `Retry-After` header. */
        const val RATE_LIMIT_BACKOFF_MS = 5_000L

        /** Hard cap on one route resolution (all ladder slots + waits + snap), enforced by cancellation. */
        const val TOTAL_TIME_BUDGET_MS = 10_000L

        /** Per-HTTP-attempt call timeout — a server slower than this is treated as down. */
        const val ATTEMPT_TIMEOUT_MS = 2_500L

        /** Only legs longer than this are eligible for bisection on failure. */
        const val BISECTION_MIN_DISTANCE_METERS = 2_500.0
        const val BISECTION_MAX_DEPTH = 5
        const val BISECTION_TIME_BUDGET_MS = 2_000L

        /** In-memory route cache capacity (LRU). */
        const val CACHE_MAX_ENTRIES = 64

        /** A cached route is served without a request for this long; after that only if the ladder fails. */
        const val CACHE_TTL_MS = 3_600_000L

        /** Waypoints are rounded to 1/scale degrees (5 decimals, ~1 m) to build the cache key. */
        const val CACHE_COORD_SCALE = 100_000.0

        /** Cooldown after an HTTP 429 with no numeric `Retry-After`, applied to the whole host. */
        const val COOLDOWN_RATE_LIMITED_MS = 60_000L

        /** Cooldown after an HTTP 5xx, applied to that backend base URL only. */
        const val COOLDOWN_SERVER_ERROR_MS = 30_000L

        /** Upper bound on any cooldown so a hostile `Retry-After` cannot disable routing. */
        const val COOLDOWN_MAX_MS = 300_000L

        /** SharedPreferences file persisting per-backend cooldown expiry (epoch ms). */
        const val COOLDOWN_PREFS_NAME = "osrm_cooldowns"
    }

    object MapConstants {
        const val DEFAULT_LAT = 48.8566
        const val DEFAULT_LON = 2.3522
        const val DEFAULT_ZOOM = 15.0

        // Tile URLs and max zoom per provider live on `MapTileSource` (`:core:model`).

        /** Street-level camera when jumping to a favorite. Preview tiles cover the wait for z18. */
        const val FAVORITE_CAMERA_ZOOM = 18.0
        const val TILE_USER_AGENT_APP = "locationjoystick"
        const val OSM_TILE_HOST = "tile.openstreetmap.org"

        /** Marker file in cacheDir; bump the suffix to wipe MapLibre's HTTP cache once more. */
        const val OSM_TILE_CACHE_BUST_MARKER = "osm_ua_cache_bust_4"

        /**
         * OkHttp per-host limit. MapLibre's default client uses 20 (native
         * `http_file_source` cap). OkHttp's own default is 5 — too slow for raster tiles.
         */
        const val OSM_MAX_REQUESTS_PER_HOST = 20

        /** MapLibre ambient (tile) cache cap. Default is 50 MB; larger keeps panning/reloads off OSM's servers. */
        const val OSM_AMBIENT_CACHE_MAX_BYTES = 200L * 1024 * 1024
        const val TILESET_VERSION = "2.2.0"

        /**
         * Low-zoom raster under the detail layer. A teleport to an uncached area can paint
         * a few z12 tiles immediately while z15–19 fill in. Same tile URL / OkHttp client.
         */
        const val OSM_PREVIEW_MAX_ZOOM = 12f

        /** Instant camera jump when the spoofed position moves farther than this (teleport). */
        const val SNAP_CAMERA_DISTANCE_METERS = 2_000.0
        const val EMPTY_MAP_STYLE_URI = "asset://empty.json"
        const val OSM_SOURCE_ID = "osm-source"
        const val OSM_LAYER_ID = "osm-layer"
        const val OSM_PREVIEW_SOURCE_ID = "osm-preview-source"
        const val OSM_PREVIEW_LAYER_ID = "osm-preview-layer"
        const val PANEL_OSM_SOURCE_ID = "panel-osm-source"
        const val PANEL_OSM_LAYER_ID = "panel-osm-layer"
        const val PANEL_OSM_PREVIEW_SOURCE_ID = "panel-osm-preview-source"
        const val PANEL_OSM_PREVIEW_LAYER_ID = "panel-osm-preview-layer"
        const val EPHEMERAL_ROUTE_SOURCE_ID = "ephemeral-route-source"
        const val EPHEMERAL_ROUTE_LAYER_ID = "ephemeral-route-layer"
        const val EPHEMERAL_ENDPOINTS_SOURCE_ID = "ephemeral-endpoints-source"
        const val EPHEMERAL_ENDPOINTS_LAYER_ID = "ephemeral-endpoints-layer"
        const val POSITION_DOT_RADIUS = 12f
        const val ROUTE_POINT_RADIUS = 8f
        const val POINT_STROKE_WIDTH = 2f
        const val JITTER_RADIUS_CIRCLE_SEGMENTS = 32
        const val JITTER_RADIUS_FILL_OPACITY = 0.15f
        const val JITTER_RADIUS_OUTLINE_OPACITY = 0.6f
        const val JITTER_RADIUS_OUTLINE_WIDTH = 1.5f
    }

    object NominatimConstants {
        const val SEARCH_URL = "https://nominatim.openstreetmap.org/search"
        const val REVERSE_URL = "https://nominatim.openstreetmap.org/reverse"
        const val SEARCH_DEBOUNCE_MS = 300L
        const val CONNECT_TIMEOUT_MS = 5000
        const val READ_TIMEOUT_MS = 5000
        const val RECENT_SEARCHES_MAX_COUNT = 5

        /** Minimum gap between search request starts (Nominatim usage policy: 1 request/second). */
        const val MIN_REQUEST_INTERVAL_MS = 1_100L

        /** In-memory search cache capacity (LRU). */
        const val CACHE_MAX_ENTRIES = 32

        /** A cached search is served without a request for this long; after that only if the request fails. */
        const val CACHE_TTL_MS = 86_400_000L
    }

    object ElevationConstants {
        const val BASE_URL = "https://api.open-meteo.com/v1/elevation"
        const val CONNECT_TIMEOUT_MS = 5000
        const val READ_TIMEOUT_MS = 5000

        /** In-memory elevation cache capacity (LRU, no TTL: ground elevation is static). */
        const val CACHE_MAX_ENTRIES = 64

        /** Lookups are rounded to 1/scale degrees (3 decimals, ~111 m cell) to build the cache key. */
        const val CACHE_COORD_SCALE = 1_000.0
    }

    object ExportConstants {
        const val SCHEMA_VERSION = 2
        const val FILENAME_PREFIX = "locationjoystick-export"
        const val MIME_TYPE = "application/json"
        const val GPX_VERSION = "1.1"
        const val GPX_CREATOR = "locationjoystick"

        /** Maximum GPX file size accepted for import (10 MB). Larger files are rejected to prevent OOM. */
        const val MAX_GPX_IMPORT_SIZE_BYTES = 10 * 1024 * 1024L

        /** GPS Joystick (and similar) tracks larger than this are skipped with a notice instead of imported. */
        const val MAX_GPX_ROUTE_WAYPOINTS = 2_000
    }

    object NotificationConstants {
        const val ID_ACTIVE = 1001
        const val ID_PERMISSION_ERROR = 1002
        const val CHANNEL_ID_ACTIVE = "location_spoof_channel"
        const val CHANNEL_ID_PERMISSION_ERROR = "location_perm_error_channel"
        const val CHANNEL_ID_ACTIVE_MINIMIZED = "location_spoof_channel_minimized"

        // User-visible channel/notification text lives in :core:location's strings.xml
        // (MockLocationNotification.kt), not here — only technical IDs stay in AppConstants.
    }

    object ServiceConstants {
        const val MOCK_LOCATION_SERVICE_CLASS = "com.locationjoystick.core.location.MockLocationService"
        const val JOYSTICK_SERVICE_CLASS = "com.locationjoystick.feature.joystick.impl.JoystickOverlayService"
        const val WIDGET_SERVICE_CLASS = "com.locationjoystick.feature.widget.impl.FloatingWidgetService"
        const val ACTION_START = "com.locationjoystick.core.location.ACTION_START"
        const val ACTION_STOP = "com.locationjoystick.core.location.ACTION_STOP"
        const val ACTION_PARK_KEEP_WIDGET = "com.locationjoystick.core.location.ACTION_PARK_KEEP_WIDGET"
        const val ACTION_UPDATE_POSITION = "com.locationjoystick.core.location.ACTION_UPDATE_POSITION"
        const val ACTION_ROUTE_REPLAY_START = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_START"
        const val ACTION_ROUTE_REPLAY_PAUSE = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_PAUSE"
        const val ACTION_ROUTE_REPLAY_RESUME = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_RESUME"
        const val ACTION_ROUTE_REPLAY_STOP = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_STOP"
        const val ACTION_ROUTE_REPLAY_CANCEL = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_CANCEL"
        const val ACTION_ROUTE_REPLAY_JUMP_NEXT = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_JUMP_NEXT"
        const val ACTION_ROUTE_REPLAY_JUMP_PREVIOUS = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_JUMP_PREVIOUS"
        const val ACTION_ROUTE_APPEND_WAYPOINT = "com.locationjoystick.core.location.ACTION_ROUTE_APPEND_WAYPOINT"
        const val EXTRA_ROUTE_ID = "extra_route_id"
        const val EXTRA_IS_BACKWARD = "extra_is_backward"
        const val EXTRA_SPEED_MS = "extra_speed_ms"
        const val EXTRA_BEARING = "extra_bearing"
        const val EXTRA_IS_TELEPORT = "extra_is_teleport"
        const val EXTRA_WAYPOINT_LAT = "extra_waypoint_lat"
        const val EXTRA_WAYPOINT_LON = "extra_waypoint_lon"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val EXTRA_IS_EPHEMERAL = "extra_is_ephemeral"
        const val EXTRA_IS_LOOPING = "extra_is_looping"
        const val EXTRA_RETURN_LAT = "extra_return_lat"
        const val EXTRA_RETURN_LON = "extra_return_lon"
        const val EXTRA_FOLLOW_ROADS_TO_START = "extra_follow_roads_to_start"
        const val EXTRA_TELEPORT_TO_START = "extra_teleport_to_start"
        const val EXTRA_IS_PLANTING = "extra_is_planting"
        const val EXTRA_TELEPORT_BETWEEN_WAYPOINTS = "extra_teleport_between_waypoints"
        const val EXTRA_TELEPORT_BETWEEN_DELAY_SECONDS = "extra_teleport_between_delay_seconds"

        /** Compact encoding of ephemeral waypoints: "lat,lon;lat,lon;...". Replaces the old parallel DoubleArray extras. */
        const val EXTRA_EPHEMERAL_WAYPOINTS = "extra_ephemeral_waypoints"
        const val EXTRA_NAVIGATE_TO_MAP = "navigate_to_map"
        const val EXTRA_NAVIGATE_TO_FAVORITES = "navigate_to_favorites"
        const val EXTRA_NAVIGATE_TO_ROUTES = "navigate_to_routes"
        const val EXTRA_NAVIGATE_TO_ROUTE_CREATOR = "navigate_to_route_creator"
        const val EXTRA_NAVIGATE_TO_CAPTURE = "navigate_to_capture"
        const val ACTION_ENTER_FOLLOWER = "com.locationjoystick.core.location.ACTION_ENTER_FOLLOWER"
        const val ACTION_EXIT_FOLLOWER = "com.locationjoystick.core.location.ACTION_EXIT_FOLLOWER"
        const val ACTION_FOLLOWER_TELEPORT = "com.locationjoystick.core.location.ACTION_FOLLOWER_TELEPORT"
        const val EXTRA_FOLLOWER_HOST = "extra_follower_host"
        const val EXTRA_FOLLOWER_PORT = "extra_follower_port"
        const val EXTRA_FOLLOWER_GROUP_ID = "extra_follower_group_id"
        const val ACTION_START_LEADER = "com.locationjoystick.core.location.ACTION_START_LEADER"
        const val ACTION_EXIT_LEADER = "com.locationjoystick.core.location.ACTION_EXIT_LEADER"
        const val EXTRA_LEADER_GROUP_ID = "extra_leader_group_id"
        const val ACTION_OVERLAY_SHOW = "com.locationjoystick.action.OVERLAY_SHOW"
        const val ACTION_OVERLAY_HIDE = "com.locationjoystick.action.OVERLAY_HIDE"
        const val EXTRA_SHOW_OVERLAY = "extra_show_overlay"
    }

    object DataStoreConstants {
        const val FILE_NAME = "app_preferences"
        const val DEFAULT_REMEMBER_LAST_LOCATION = true
        const val DEFAULT_LAST_TELEPORT_TIME_MS = 0L
        const val DEFAULT_THEME_MODE = "DARK"
        const val DEFAULT_WHATS_NEW_LAST_SEEN_VERSION = ""
        const val DEFAULT_UPDATE_CHECK_LAST_CHECKED_AT_MS = 0L
        const val DEFAULT_UPDATE_CHECK_LATEST_VERSION = ""
        const val DEFAULT_UPDATE_CHECK_DISMISSED_VERSION = ""
        const val KEY_GROUP_ROLE = "group_role"
        const val KEY_GROUP_ID = "group_id"
        const val KEY_GROUP_LEADER_HOST = "group_leader_host"
        const val KEY_GROUP_LEADER_PORT = "group_leader_port"
        const val KEY_GROUP_FOLLOWER_MODE_ENABLED = "group_follower_mode_enabled"
        const val KEY_GROUP_FOLLOW_LEADER_TELEPORTS = "group_follow_leader_teleports"
        const val KEY_GROUP_SHARING_ENABLED = "group_sharing_enabled"
        const val KEY_CAPTURE_MODE_ENABLED = "capture_coordinates_mode_enabled"
        const val KEY_CAPTURE_ENABLED = "capture_coordinates_enabled"
        const val KEY_CAPTURE_JUMP_ENABLED = "capture_coordinates_jump_enabled"
        const val KEY_CAPTURE_POINTS = "capture_coordinates_points"
        const val KEY_CAPTURE_PREVIOUS_BROWSER = "capture_coordinates_previous_browser"
        const val KEY_CAPTURE_SETUP_RESET = "capture_coordinates_setup_reset"
        const val KEY_CAPTURE_HELPER_OPEN = "capture_coordinates_helper_open"
    }

    object CooldownConstants {
        data class CooldownTier(
            val distanceMeters: Double,
            val cooldownSeconds: Long,
        )

        val TIERS: List<CooldownTier> =
            listOf(
                CooldownTier(0.0, 0L),
                CooldownTier(10.0, 3L),
                CooldownTier(100.0, 15L),
                CooldownTier(500.0, 30L),
                CooldownTier(1_000.0, 120L),
                CooldownTier(5_000.0, 360L),
                CooldownTier(10_000.0, 660L),
                CooldownTier(25_000.0, 840L),
                CooldownTier(30_000.0, 1320L),
                CooldownTier(65_000.0, 1500L),
                CooldownTier(81_000.0, 2100L),
                CooldownTier(100_000.0, 2700L),
                CooldownTier(250_000.0, 3600L),
                CooldownTier(500_000.0, 4500L),
                CooldownTier(750_000.0, 5400L),
                CooldownTier(1_000_000.0, 7200L),
            )
    }

    object JoystickConstants {
        const val SIZE_DP = 90
        const val STEP_SECONDS = 0.1
        const val STEP_MS = 100L
        const val KNOB_RADIUS_FRACTION = 0.25f
        const val DEADZONE_FRACTION = 0.15f
        const val DRAG_HANDLE_FRACTION = 0.28f

        /** Outer disc fill. High enough to read on light apps, still see-through on dark ones. */
        const val OUTER_ALPHA = 128
        const val OUTER_BORDER_ALPHA = 190
        const val KNOB_ALPHA = 255
        const val KNOB_EDGE_ALPHA = 140

        /** Stick ring, packed RGB. */
        const val KNOB_EDGE_RGB = 0x303036

        /** Outer disc ring. Same gray family as [KNOB_EDGE_RGB], slightly lighter for white apps. */
        const val OUTER_BORDER_RGB = 0x686870
    }

    object RouteConstants {
        const val WAYPOINT_SNAP_THRESHOLD_METERS = 1.0
        const val MIN_TELEPORT_WAIT_SECONDS = 1
        const val DEFAULT_TELEPORT_WAIT_SECONDS = 5
        const val PLANTING_DEFAULT_RADIUS_METERS = 35.0
        const val ROUTE_MIN_RADIUS_METERS = 5.0
        val PLANTING_MAX_RADIUS_METERS = PlantingConstants.MAX_RADIUS_METERS
        val PLANTING_CHORD_METERS = PlantingConstants.CHORD_METERS
        const val PLANTING_MIN_VERTICES = 8
        const val PLANTING_MAX_VERTICES = 48

        /** Linger at each hopped stop before the next hop or planting ring. */
        const val TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS = 8
        const val TELEPORT_BETWEEN_MIN_DELAY_SECONDS = 0
        const val TELEPORT_BETWEEN_MAX_DELAY_SECONDS = 600

        /** Reserved Room id for map/widget paste Start without Save route. Not a `hot_route_` id. */
        const val PASTE_TEMP_ROUTE_ID = "paste_temp_route"

        /** Display name of [PASTE_TEMP_ROUTE_ID]. Named UUID saves are never matched by this title. */
        const val PASTE_TEMP_ROUTE_NAME = "Temp Route from Paste"
    }

    object DatabaseConstants {
        const val DATABASE_NAME = "locationjoystick.db"
    }

    object TopBarConstants {
        /** Place-name suffix on the idle Start control. Extra characters are dropped. */
        const val LOCATION_LABEL_MAX_CHARS = 18
    }

    object AppInfo {
        const val VERSION_NAME = "0.22.0" // x-release-please-version
        const val GITHUB_REPO_SLUG = "shortcuts/locationjoystick"
        const val GITHUB_ISSUES_URL = "https://github.com/$GITHUB_REPO_SLUG/issues/new?template=bug_report.yml"
        const val DOCS_URL = "https://locationjoystick.shrtcts.fr/"
        const val TROUBLESHOOTING_URL = "https://locationjoystick.shrtcts.fr/troubleshooting.html"
        const val CAPTURE_GUIDE_URL = "https://locationjoystick.shrtcts.fr/capture-coordinates.html"
        const val CHANGELOG_URL = "https://locationjoystick.shrtcts.fr/changelog.html"
        const val DEEP_LINK_HOST = "locationjoystick.shrtcts.fr"

        fun buildDeepLink(
            lat: Double,
            lon: Double,
        ) = "https://$DEEP_LINK_HOST/?lat=$lat&lon=$lon"
    }

    /** Home-screen "new version available" badge (see docs/features/update-check.md). */
    object UpdateCheckConstants {
        const val GITHUB_API_URL = "https://api.github.com/repos/${AppInfo.GITHUB_REPO_SLUG}/releases/latest"
        const val CONNECT_TIMEOUT_MS = 5000
        const val READ_TIMEOUT_MS = 5000
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        fun userAgent() = "locationjoystick/${AppInfo.VERSION_NAME}"

        // GitHub's release-tag URL has a fixed shape, so it is derived from the cached version.
        fun releaseUrl(version: String) = "https://github.com/${AppInfo.GITHUB_REPO_SLUG}/releases/tag/v$version"
    }

    /**
     * Curated "what's new" highlights for the current [AppInfo.VERSION_NAME], shown in the
     * in-app What's New popup (see docs/features/whats-new.md). Update alongside every release
     * that has user-visible changes — mirrors the curation already done for docs/wiki/changelog.html.
     */
    object WhatsNewConstants {
        // Per-version JSON, authored alongside docs/wiki/changelog.html — see docs/features/whats-new.md.
        const val BASE_URL = "https://locationjoystick.shrtcts.fr/changelog/"
        const val CONNECT_TIMEOUT_MS = 5000
        const val READ_TIMEOUT_MS = 5000

        // Strips a pre-release suffix (e.g. "0.19.0-alpha1" -> "0.19.0") since changelog JSON
        // is authored per release version, not per alpha/beta tag.
        fun buildUrl(version: String) = "$BASE_URL${version.substringBefore("-")}.json"

        // Packed into the APK for offline release highlights.
        // Strips a pre-release suffix (e.g. "0.20.0-alpha1" -> "0.20.0.json").
        fun assetFileName(version: String) = "${version.substringBefore("-")}.json"
    }

    object AnimationConstants {
        const val SPRING_DAMPING_RATIO = 0.85f
        const val SPRING_STIFFNESS = 400f
    }

    object TimeConstants {
        const val SECONDS_PER_HOUR = 3600
        const val SECONDS_PER_MINUTE = 60
    }

    object SyncConstants {
        const val POLL_INTERVAL_MS = 1000L
        const val POLL_TIMEOUT_MS = 800L
        const val SERVER_BACKLOG = 5
        const val POSITION_STALE_THRESHOLD_MS = 5000L
        const val NSD_SERVICE_TYPE = "_ljsync._tcp."
        const val NSD_DISCOVERY_TIMEOUT_MS = 10_000L
        const val GROUP_CODE_LENGTH = 6

        /**
         * At [POLL_INTERVAL_MS] (1s) + up to [POLL_TIMEOUT_MS] per attempt, 5 failures gave up
         * within ~5-9s — too tight for a brief Wi-Fi reassociation or Doze-deferred network access
         * on a background service. Widened to give the leader more real-world chances before the
         * follower attempts NSD re-discovery.
         */
        const val MAX_CONSECUTIVE_POLL_FAILURES = 15
        const val EXPORT_FETCH_TIMEOUT_MS = 8000L

        /** NSD re-discovery attempts after poll failures exhaust, before actually leaving the group. */
        const val NSD_REDISCOVERY_RETRY_COUNT = 2
    }

    object TapToWalkConstants {
        // Calibrated for a fully zoomed-out AR game map: two known landmarks ~40.5 m apart spanning ~173 px → 0.23 m/px.
        const val DEFAULT_SCALE_MPX = 0.23
        const val MIN_SCALE_MPX = 0.01
        const val MAX_SCALE_MPX = 1.0
    }

    object CompassTrackingConstants {
        // Fixed search window — every tested AR/GPS-spoofing game places its compass top-right,
        // so this needs no per-user calibration. Covers the right 45% / top 35% of the screen.
        const val SEARCH_X_MIN_PCT = 0.55f
        const val SEARCH_Y_MAX_PCT = 0.35f

        // Icon-sized blob bounds, as a fraction of the screen's short side — rejects both stray
        // noise pixels (too small) and unrelated large red UI elements like a gym marker or raid
        // egg (too big), instead of trusting every red pixel in a wide region.
        const val MIN_ICON_FRACTION = 0.008f
        const val MAX_ICON_FRACTION = 0.06f
        const val MIN_RED_PIXELS = 20
    }

    object FollowerRestorationConstants {
        /** Initial retry delay in milliseconds */
        const val INITIAL_RETRY_DELAY_MS = 500L

        /** Maximum retry delay in milliseconds (exponential backoff caps here) */
        const val MAX_RETRY_DELAY_MS = 30_000L

        /** Maximum number of NSD discovery attempts before giving up */
        const val MAX_RETRY_ATTEMPTS = 5

        /** Random jitter range in milliseconds (±) to avoid thundering herd */
        const val RETRY_JITTER_MS = 100L
    }

    object LocaleConstants {
        // Deliberately a plain SharedPreferences file, not DataStore: attachBaseContext()
        // must return synchronously on the main thread, and DataStore reads are Flow-based/async.
        const val PREFS_NAME = "locale_prefs"
        const val KEY_LANGUAGE_TAG = "language_tag"
    }
}
