package com.locationjoystick.core.common.constants

object AppConstants {
    object LocationConstants {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val METERS_PER_LATITUDE_DEGREE = 111_320.0
        const val UPDATE_INTERVAL_MS = 1000L
        const val LOCATION_ACCURACY_FINE = 3.0f
        const val WALK_ARRIVAL_THRESHOLD_METERS = 1.0
        const val DEFAULT_REPLAY_SPEED_MS = ProfileConstants.WALK_SPEED_MPS
        const val RDP_SIMPLIFICATION_EPSILON_METERS = 5.0
        const val DEGREES_IN_CIRCLE = 360.0
        const val CARDINAL_SNAP_STEP_DEGREES = 45.0
    }

    object ProfileConstants {
        const val PROFILE_ID_WALK = "walk"
        const val PROFILE_ID_RUN = "run"
        const val PROFILE_ID_BIKE = "bike"
        const val WALK_SPEED_MPS = 0.5556
        const val RUN_SPEED_MPS = 2.2222
        const val BIKE_SPEED_MPS = 4.1667
        const val DEFAULT_ACTIVE_PROFILE_ID = PROFILE_ID_WALK
        const val MIN_SPEED_MS = 0.1
        const val MAX_SPEED_MS = 15.0
        const val ANTI_CHEAT_WARNING_THRESHOLD_MS = 8.0
        const val DEFAULT_SPEED_UNIT = "KMH"
    }

    object JitterConstants {
        const val DEFAULT_IDLE_RADIUS_METERS = 0.8
        const val DEFAULT_MOVING_RADIUS_METERS = 0.3
        const val MAX_RADIUS_METERS = 50.0
        const val DEFAULT_MOVING_INTERVAL_SECONDS = 10
        const val LONGITUDINAL_JITTER_FRACTION = 0.2
        const val DEFAULT_IDLE_INTERVAL_SECONDS = 30
        const val MIN_INTERVAL_SECONDS = 1
        const val MAX_INTERVAL_SECONDS = 30
        const val ACCURACY_MIN = 2.0f
        const val ACCURACY_MAX = 5.0f
        const val ACCURACY_PERTURBATION_RANGE = 3.0
        const val SPEED_IDLE_VARIATION_PCT_DEFAULT = 5
        const val SPEED_MOVING_VARIATION_PCT_DEFAULT = 5
        const val SPEED_VARIATION_PCT_MIN = 0
        const val SPEED_VARIATION_PCT_MAX = 50
    }

    object RealismConstants {
        const val DEFAULT_ALTITUDE_METERS = 35.0
        const val ALTITUDE_SIGMA_METERS = 0.25
        const val ALTITUDE_DRIFT_PER_SECOND_METERS = 0.0
        const val ALTITUDE_CLAMP_RADIUS_METERS = 25.0
        const val VERTICAL_ACCURACY_METERS = 4.0f
        const val BEARING_ACCURACY_DEGREES = 3.0f
        const val BEARING_NOISE_DEGREES = 5.0f
        const val SPEED_ACCURACY_MPS = 0.3f
        const val SATELLITES_MIN = 7
        const val SATELLITES_MAX = 14
        const val USED_IN_FIX_MIN = 6
        const val USED_IN_FIX_MAX = 12
        const val SATELLITE_UPDATE_INTERVAL_MS = 5_000L
        const val WARMUP_DURATION_SECONDS = 30
        const val WARMUP_INITIAL_ACCURACY_METERS = 50.0f
        const val WARMUP_ENABLED_DEFAULT = false
        const val SUSPENDED_MOCKING_ENABLED_DEFAULT = false
        const val PEDOMETER_MOCKING_ENABLED_DEFAULT = false
        const val SUSPENDED_PUSH_DURATION_MS = 8_000L
        const val SUSPENDED_PAUSE_DURATION_MS = 2_000L
        const val SUSPENDED_PAUSE_JITTER_MS = 800L
        const val BEARING_HOLD_ON_IDLE_DEFAULT = true
        const val ALTITUDE_ENABLED_DEFAULT = true
        const val SATELLITE_EXTRAS_ENABLED_DEFAULT = true
        const val RECOMMENDED_IDLE_RADIUS_METERS = 0.8
        const val ALTITUDE_HUMAN_OFFSET_METERS = 0.8
        const val ALTITUDE_HUMAN_OFFSET_JITTER_PCT = 0.05
        const val ALTITUDE_HUMAN_OFFSET_CLAMP_FACTOR = 0.5
    }

    object PedometerConstants {
        const val MAX_WALKING_SPEED_MPS = 4.0f
        const val STRIDE_BASE_METERS = 0.4f
        const val STRIDE_SPEED_FACTOR = 0.25f
        const val STRIDE_JITTER_PCT = 0.15f
    }

    object RoamingConstants {
        const val DEFAULT_RADIUS_METERS = 2000.0
        const val DEFAULT_DURATION_SECONDS = 1800L
        const val OSRM_PROFILE_FOOT = "foot"
        const val OSRM_PROFILE_CYCLING = "cycling"
        const val DEFAULT_TRANSPORT_MODE = ProfileConstants.PROFILE_ID_WALK
        const val WAYPOINT_ARRIVAL_THRESHOLD_METERS = 5.0
        const val DEFAULT_DISTANCE_METERS = 1_000.0
        const val DEFAULT_FOLLOW_ROADS = true
        const val DEFAULT_RETURN_TO_START = true
        const val RADIUS_MIN_METERS = 1_000.0
        const val RADIUS_MAX_METERS = 100_000.0
        const val DISTANCE_MIN_METERS = 50.0
        const val DISTANCE_MAX_METERS = 50_000.0
        const val WAYPOINTS_PER_1000M = 30
        const val MAX_OSRM_PLANNING_CALLS = 50
    }

    object OsrmConstants {
        const val BASE_URL = "https://router.project-osrm.org/"
        const val OVERVIEW = "full"
        const val GEOMETRIES = "geojson"
    }

    object MapConstants {
        const val DEFAULT_LAT = 48.8566
        const val DEFAULT_LON = 2.3522
        const val DEFAULT_ZOOM = 15.0
        const val OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        const val TILESET_VERSION = "2.2.0"
        const val OSM_MAX_ZOOM = 19f
        const val EMPTY_MAP_STYLE_URI = "asset://empty.json"
        const val OSM_SOURCE_ID = "osm-source"
        const val OSM_LAYER_ID = "osm-layer"
        const val PANEL_OSM_SOURCE_ID = "panel-osm-source"
        const val PANEL_OSM_LAYER_ID = "panel-osm-layer"
        const val EPHEMERAL_ROUTE_SOURCE_ID = "ephemeral-route-source"
        const val EPHEMERAL_ROUTE_LAYER_ID = "ephemeral-route-layer"
        const val EPHEMERAL_ENDPOINTS_SOURCE_ID = "ephemeral-endpoints-source"
        const val EPHEMERAL_ENDPOINTS_LAYER_ID = "ephemeral-endpoints-layer"
        const val POSITION_DOT_RADIUS = 12f
        const val ROUTE_POINT_RADIUS = 8f
        const val POINT_STROKE_WIDTH = 2f
    }

    object NominatimConstants {
        const val SEARCH_URL = "https://nominatim.openstreetmap.org/search"
        const val REVERSE_URL = "https://nominatim.openstreetmap.org/reverse"
        const val SEARCH_DEBOUNCE_MS = 300L
        const val CONNECT_TIMEOUT_MS = 5000
        const val READ_TIMEOUT_MS = 5000
        const val RECENT_SEARCHES_MAX_COUNT = 5
    }

    object ExportConstants {
        const val SCHEMA_VERSION = 2
        const val FILENAME_PREFIX = "locationjoystick-export"
        const val MIME_TYPE = "application/json"
        const val GPX_MIME_TYPE = "application/gpx+xml"
        const val GPX_VERSION = "1.1"
        const val GPX_CREATOR = "locationjoystick"
        const val QR_CHUNK_SIZE_LIMIT = 2400

        /** Maximum GPX file size accepted for import (10 MB). Larger files are rejected to prevent OOM. */
        const val MAX_GPX_IMPORT_SIZE_BYTES = 10 * 1024 * 1024L
    }

    object NotificationConstants {
        const val ID_ACTIVE = 1001
        const val ID_PERMISSION_ERROR = 1002
        const val CHANNEL_ID_ACTIVE = "location_spoof_channel"
        const val CHANNEL_ID_PERMISSION_ERROR = "location_perm_error_channel"
        const val CHANNEL_NAME_ACTIVE = "Location Spoofing"
        const val CHANNEL_DESC_ACTIVE = "Active while mock location is running"
        const val CHANNEL_NAME_PERMISSION_ERROR = "Permission Errors"
        const val CHANNEL_DESC_PERMISSION_ERROR = "Shown when required permissions are missing"
        const val TITLE_PERMISSION_ERROR = "Permissions missing"
        const val TEXT_PERMISSION_ERROR = "Open the app and complete setup to start spoofing."
        const val TITLE_ACTIVE = "Mock location active"
        const val TEXT_ACTIVE = "locationjoystick is spoofing your GPS position"
        const val ACTION_STOP = "Stop"
        const val ACTION_OPEN_MAP = "Map"
        const val ACTION_OPEN_FAVORITES = "Favorites"
        const val ACTION_OPEN_ROUTES = "Routes"
        const val ACTION_PAUSE = "Pause"
        const val ACTION_RESUME = "Resume"
    }

    object ServiceConstants {
        const val MOCK_LOCATION_SERVICE_CLASS = "com.locationjoystick.core.location.MockLocationService"
        const val JOYSTICK_SERVICE_CLASS = "com.locationjoystick.feature.joystick.impl.JoystickOverlayService"
        const val WIDGET_SERVICE_CLASS = "com.locationjoystick.feature.widget.impl.FloatingWidgetService"
        const val ACTION_START = "com.locationjoystick.core.location.ACTION_START"
        const val ACTION_STOP = "com.locationjoystick.core.location.ACTION_STOP"
        const val ACTION_UPDATE_POSITION = "com.locationjoystick.core.location.ACTION_UPDATE_POSITION"
        const val ACTION_ROUTE_REPLAY_START = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_START"
        const val ACTION_ROUTE_REPLAY_PAUSE = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_PAUSE"
        const val ACTION_ROUTE_REPLAY_RESUME = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_RESUME"
        const val ACTION_ROUTE_REPLAY_STOP = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_STOP"
        const val ACTION_ROUTE_REPLAY_CANCEL = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_CANCEL"
        const val ACTION_ROUTE_APPEND_WAYPOINT = "com.locationjoystick.core.location.ACTION_ROUTE_APPEND_WAYPOINT"
        const val EXTRA_ROUTE_ID = "extra_route_id"
        const val EXTRA_IS_BACKWARD = "extra_is_backward"
        const val EXTRA_SPEED_MS = "extra_speed_ms"
        const val EXTRA_BEARING = "extra_bearing"
        const val EXTRA_WAYPOINT_LAT = "extra_waypoint_lat"
        const val EXTRA_WAYPOINT_LON = "extra_waypoint_lon"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val EXTRA_IS_EPHEMERAL = "extra_is_ephemeral"
        const val EXTRA_IS_LOOPING = "extra_is_looping"
        const val EXTRA_RETURN_LAT = "extra_return_lat"
        const val EXTRA_RETURN_LON = "extra_return_lon"

        /** Compact encoding of ephemeral waypoints: "lat,lon;lat,lon;...". Replaces the old parallel DoubleArray extras. */
        const val EXTRA_EPHEMERAL_WAYPOINTS = "extra_ephemeral_waypoints"
        const val EXTRA_NAVIGATE_TO_MAP = "navigate_to_map"
        const val EXTRA_NAVIGATE_TO_FAVORITES = "navigate_to_favorites"
        const val EXTRA_NAVIGATE_TO_ROUTES = "navigate_to_routes"
        const val EXTRA_NAVIGATE_TO_ROUTE_CREATOR = "navigate_to_route_creator"
        const val ACTION_OVERLAY_SHOW = "com.locationjoystick.action.OVERLAY_SHOW"
        const val ACTION_OVERLAY_HIDE = "com.locationjoystick.action.OVERLAY_HIDE"
        const val EXTRA_SHOW_OVERLAY = "extra_show_overlay"
    }

    object DataStoreConstants {
        const val FILE_NAME = "app_preferences"
        const val DEFAULT_REMEMBER_LAST_LOCATION = true
        const val DEFAULT_LAST_TELEPORT_TIME_MS = 0L
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
        const val OUTER_ALPHA = 80
    }

    object WidgetConstants {
        const val PANEL_ANIMATION_DURATION_MS = 200L
    }

    object RouteConstants {
        const val WAYPOINT_SNAP_THRESHOLD_METERS = 1.0
    }

    object DatabaseConstants {
        const val DATABASE_NAME = "locationjoystick.db"
    }

    object AppInfo {
        const val VERSION_NAME = "0.9.0" // x-release-please-version
        const val GITHUB_URL = "https://github.com/shortcuts/locationjoystick"
        const val GITHUB_ISSUES_URL = "https://github.com/shortcuts/locationjoystick/issues/new?template=bug_report.yml"
        const val GITHUB_FEATURE_REQUEST_URL = "https://github.com/shortcuts/locationjoystick/issues/new?template=feature_request.md"
        const val DOCS_URL = "https://shortcuts.github.io/locationjoystick/"
        const val PRIVACY_POLICY_URL = "https://shortcuts.github.io/locationjoystick/privacy.html"
        const val ACKNOWLEDGEMENTS_URL = "https://shortcuts.github.io/locationjoystick/acknowledgements.html"
        const val DEEP_LINK_HOST = "locationjoystick.shrtcts.fr"

        fun buildDeepLink(
            lat: Double,
            lon: Double,
        ) = "https://$DEEP_LINK_HOST/?lat=$lat&lon=$lon"
    }

    object UnitConversionConstants {
        const val METERS_PER_MILE = 1609.344
        const val FEET_PER_METER = 3.28084
    }

    object AnimationConstants {
        const val MAP_CAMERA_DURATION_MS = 500
        const val SPRING_DAMPING_RATIO = 0.85f
        const val SPRING_STIFFNESS = 400f
        const val SCALE_IN_INITIAL = 0.95f
    }

    object TimeConstants {
        const val SECONDS_PER_HOUR = 3600
        const val SECONDS_PER_MINUTE = 60
    }

    object ElevationConstants {
        const val DEFAULT_TILT_DEGREES = 45f
        const val MIN_TILT_DEGREES = 20f
        const val MAX_TILT_DEGREES = 75f
        const val DEFAULT_NOISE_AMPLITUDE_MS2 = 0.35f
        const val DEFAULT_TILT_JITTER_DEGREES = 2.25f
        const val MIN_TILT_JITTER_DEGREES = 0f
        const val MAX_TILT_JITTER_DEGREES = 10f
        const val MIN_NOISE_AMPLITUDE_MS2 = 0f
        const val MAX_NOISE_AMPLITUDE_MS2 = 2f
        const val GRAVITY = 9.80665f
    }
}
