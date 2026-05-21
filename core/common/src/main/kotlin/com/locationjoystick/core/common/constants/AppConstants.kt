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
        const val DEFAULT_IDLE_RADIUS_METERS = 0.0
        const val DEFAULT_MOVING_RADIUS_METERS = 1.0
        const val MAX_RADIUS_METERS = 50.0
        const val DEFAULT_INTERVAL_SECONDS = 3
        const val MIN_INTERVAL_SECONDS = 1
        const val MAX_INTERVAL_SECONDS = 30
        const val ACCURACY_MIN = 2.0f
        const val ACCURACY_MAX = 5.0f
        const val ACCURACY_PERTURBATION_RANGE = 3.0
    }

    object RealismConstants {
        const val DEFAULT_ALTITUDE_METERS = 35.0
        const val ALTITUDE_SIGMA_METERS = 1.5
        const val ALTITUDE_DRIFT_PER_SECOND_METERS = 0.05
        const val ALTITUDE_CLAMP_RADIUS_METERS = 25.0
        const val VERTICAL_ACCURACY_METERS = 4.0f
        const val BEARING_ACCURACY_DEGREES = 3.0f
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
        const val SUSPENDED_PUSH_DURATION_MS = 8_000L
        const val SUSPENDED_PAUSE_DURATION_MS = 2_000L
        const val SUSPENDED_PAUSE_JITTER_MS = 800L
        const val BEARING_HOLD_ON_IDLE_DEFAULT = true
        const val ALTITUDE_ENABLED_DEFAULT = true
        const val SATELLITE_EXTRAS_ENABLED_DEFAULT = true
        const val RECOMMENDED_IDLE_RADIUS_METERS = 0.8
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
        const val EMPTY_MAP_STYLE_URI = "asset://empty.json"
        const val OSM_SOURCE_ID = "osm-source"
        const val OSM_LAYER_ID = "osm-layer"
    }

    object NominatimConstants {
        const val SEARCH_URL = "https://nominatim.openstreetmap.org/search"
        const val SEARCH_DEBOUNCE_MS = 300L
        const val CONNECT_TIMEOUT_MS = 5000
        const val READ_TIMEOUT_MS = 5000
    }

    object ExportConstants {
        const val SCHEMA_VERSION = 1
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
        const val ACTION_OVERLAY_SHOW = "com.locationjoystick.action.OVERLAY_SHOW"
        const val ACTION_OVERLAY_HIDE = "com.locationjoystick.action.OVERLAY_HIDE"
    }

    object DataStoreConstants {
        const val FILE_NAME = "app_preferences"
        const val DEFAULT_REMEMBER_LAST_LOCATION = true
        const val DEFAULT_LAST_TELEPORT_TIME_MS = 0L
    }

    object CooldownConstants {
        /** Lower-bound distance in meters for each cooldown tier (16 tiers, index-matched with [COOLDOWN_SECONDS]). */
        val DISTANCE_THRESHOLDS_METERS: List<Double> =
            listOf(
                0.0,
                10.0,
                100.0,
                500.0,
                1_000.0,
                5_000.0,
                10_000.0,
                25_000.0,
                30_000.0,
                65_000.0,
                81_000.0,
                100_000.0,
                250_000.0,
                500_000.0,
                750_000.0,
                1_000_000.0,
            )

        /** Cooldown in seconds matching each distance tier in [DISTANCE_THRESHOLDS_METERS]. */
        val COOLDOWN_SECONDS: List<Long> =
            listOf(
                0L,
                3L,
                15L,
                30L,
                120L,
                360L,
                660L,
                840L,
                1320L,
                1500L,
                2100L,
                2700L,
                3600L,
                4500L,
                5400L,
                7200L,
            )
    }

    object JoystickConstants {
        const val SIZE_DP = 90
        const val STEP_SECONDS = 0.1
        const val STEP_MS = 100L
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
        const val GITHUB_URL = "https://github.com/shortcuts/locationjoystick"
        const val GITHUB_ISSUES_URL = "https://github.com/shortcuts/locationjoystick/issues"
    }
}
