package com.locationjoystick.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.constants.AppConstants.ServiceConstants
import com.locationjoystick.core.common.util.advancePosition
import com.locationjoystick.core.common.util.calculateBearing
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.BuildConfig
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.routing.RouteReplayEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.random.Random

/**
 * Immutable snapshot of all @Volatile service state, captured once at the start of each tick by
 * [captureSnapshot] to avoid TOCTOU races between reading individual fields in [buildLocation].
 *
 * @property latitude Current spoofed latitude.
 * @property longitude Current spoofed longitude.
 * @property speedMs Current speed in m/s; 0 when stationary.
 * @property bearing Current heading in degrees; only meaningful when [speedMs] > 0.
 * @property lastNonZeroBearing The most recent bearing from a tick where [speedMs] was non-zero.
 *   Used to hold the displayed heading when the device stops moving.
 * @property mode Active [MockMode] at snapshot time.
 * @property jitterIdleRadiusMeters Gaussian noise radius applied while stationary (TELEPORT mode).
 * @property jitterMovingRadiusMeters Gaussian noise radius applied while moving.
 * @property shouldApplyMovingJitter Pre-computed gate: true when the jitter interval has elapsed.
 *   [buildLocation] uses this directly without any clock arithmetic.
 * @property altitudeMeters Seed altitude for the Gaussian random walk this tick; written back from
 *   [LocationFix.altitudeMeters] after each successful tick.
 * @property warmupStartMs Wall-clock ms ([SystemClock.elapsedRealtime]) when [startSpoofing] was
 *   called. Intentionally NOT reset on pause/resume so the warmup curve is continuous.
 * @property spoofingStartMs Same instant as [warmupStartMs]; both set together in [startSpoofing]
 *   and kept separate for semantic clarity.
 * @property warmupEnabled Whether the accuracy warm-up envelope feature is active.
 * @property bearingHoldEnabled Whether to hold the last non-zero bearing when stationary.
 * @property altitudeEnabled Whether to simulate altitude with a Gaussian random walk.
 * @property satelliteExtrasEnabled Whether to attach satellite count extras to each fix.
 * @property suspendedMockingEnabled Whether the push/pause cycle feature is active.
 * @property suspendedPhaseStartMs Timestamp of the last phase transition in the push/pause cycle.
 * @property isSuspendedPhase True when currently in the pause window of the push/pause cycle;
 *   [buildLocation] returns null for the entire duration of this phase.
 * @property cachedSatelliteCount Slow-churn total satellite count, refreshed every
 *   [AppConstants.RealismConstants.SATELLITE_UPDATE_INTERVAL_MS] ms by [captureSnapshot].
 * @property cachedUsedInFixCount Slow-churn in-fix satellite count, updated alongside
 *   [cachedSatelliteCount].
 */
internal data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val speedMs: Float,
    val bearing: Float,
    val lastNonZeroBearing: Float,
    val mode: MockMode,
    val jitterIdleRadiusMeters: Double,
    val jitterMovingRadiusMeters: Double,
    val shouldApplyMovingJitter: Boolean,
    val altitudeMeters: Double,
    val warmupStartMs: Long,
    val spoofingStartMs: Long,
    val warmupEnabled: Boolean,
    val bearingHoldEnabled: Boolean,
    val altitudeEnabled: Boolean,
    val satelliteExtrasEnabled: Boolean,
    val suspendedMockingEnabled: Boolean,
    val suspendedPhaseStartMs: Long,
    val isSuspendedPhase: Boolean,
    val cachedSatelliteCount: Int,
    val cachedUsedInFixCount: Int,
)

/**
 * Pure output of [buildLocation]: a GPS fix expressed in domain types, with no Android imports.
 * Translated into an [android.location.Location] only inside [applyToProvider].
 *
 * @property latitude Spoofed latitude, possibly perturbed by jitter.
 * @property longitude Spoofed longitude, possibly perturbed by jitter.
 * @property altitudeMeters Result of the Gaussian altitude random walk for this tick.
 * @property speedMs Speed in m/s to report to the provider.
 * @property bearing Heading in degrees after bearing-hold logic is applied.
 * @property accuracyMeters Horizontal accuracy, either from the warm-up envelope or perturbed fine accuracy.
 * @property verticalAccuracyMeters Fixed vertical accuracy constant.
 * @property bearingAccuracyDegrees Fixed bearing accuracy constant.
 * @property speedAccuracyMps Fixed speed accuracy constant.
 * @property satelliteCount Total visible satellite count, or null when satellite extras are disabled.
 * @property usedInFixCount Satellites contributing to this fix, or null when satellite extras are disabled.
 */
internal data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val speedMs: Float,
    val bearing: Float,
    val accuracyMeters: Float,
    val verticalAccuracyMeters: Float,
    val bearingAccuracyDegrees: Float,
    val speedAccuracyMps: Float,
    val satelliteCount: Int?,
    val usedInFixCount: Int?,
)

/** Adds bounded Gaussian noise to [base] accuracy, clamped to [[ACCURACY_MIN], [ACCURACY_MAX]]. */
internal fun perturbAccuracy(
    base: Float,
    random: Random,
): Float =
    (
        base +
            (
                random.nextDouble() * AppConstants.JitterConstants.ACCURACY_PERTURBATION_RANGE -
                    AppConstants.JitterConstants.ACCURACY_PERTURBATION_RANGE / 2
            ).toFloat()
    ).coerceIn(AppConstants.JitterConstants.ACCURACY_MIN, AppConstants.JitterConstants.ACCURACY_MAX)

/**
 * Pure, side-effect-free GPS fix builder. No Android imports; [random] is injectable for testing.
 *
 * Execution order: suspended-phase check → altitude Gaussian walk → bearing hold → position jitter
 * → warm-up accuracy envelope → accuracy perturbation → satellite extras.
 *
 * @param state Immutable snapshot of all service state for this tick.
 * @param nowMs [SystemClock.elapsedRealtime] at the start of the tick, used for the warm-up curve.
 * @param random Source of randomness; pass [Random.Default] in production.
 * @return A completed [LocationFix], or null when [state.isSuspendedPhase] is true (skip this tick).
 */
internal fun buildLocation(
    state: LocationSnapshot,
    nowMs: Long,
    random: Random,
): LocationFix? {
    // Suspended early-return
    if (state.isSuspendedPhase) return null

    // Altitude with Gaussian random walk
    val newAltitude =
        if (state.altitudeEnabled) {
            val u1 = random.nextDouble().coerceAtLeast(Double.MIN_VALUE)
            val u2 = random.nextDouble()
            val mag =
                AppConstants.RealismConstants.ALTITUDE_SIGMA_METERS *
                    kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * kotlin.math.PI * u2)
            (state.altitudeMeters + mag + AppConstants.RealismConstants.ALTITUDE_DRIFT_PER_SECOND_METERS)
                .coerceIn(
                    AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS -
                        AppConstants.RealismConstants.ALTITUDE_CLAMP_RADIUS_METERS,
                    AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS +
                        AppConstants.RealismConstants.ALTITUDE_CLAMP_RADIUS_METERS,
                )
        } else {
            AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS
        }

    // Bearing hold
    val outBearing =
        when {
            state.speedMs == 0f && state.bearingHoldEnabled -> state.lastNonZeroBearing
            state.speedMs == 0f -> 0f
            else -> state.bearing
        }

    // Jitter (position)
    val (outLat, outLon) =
        when {
            state.mode == MockMode.TELEPORT && state.jitterIdleRadiusMeters > 0.0 -> {
                val u1 = random.nextDouble().coerceAtLeast(Double.MIN_VALUE)
                val u2 = random.nextDouble()
                val mag = state.jitterIdleRadiusMeters * kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1))
                val angle = 2.0 * kotlin.math.PI * u2
                val dlat = mag * kotlin.math.cos(angle) / AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
                val dlon =
                    mag * kotlin.math.sin(angle) /
                        (
                            AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE *
                                kotlin.math.cos(Math.toRadians(state.latitude))
                        )
                Pair(state.latitude + dlat, state.longitude + dlon)
            }

            state.mode != MockMode.TELEPORT && state.shouldApplyMovingJitter &&
                state.jitterMovingRadiusMeters > 0.0 -> {
                val u1 = random.nextDouble().coerceAtLeast(Double.MIN_VALUE)
                val u2 = random.nextDouble()
                val mag = state.jitterMovingRadiusMeters * kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1))
                val angle = 2.0 * kotlin.math.PI * u2
                val dlat = mag * kotlin.math.cos(angle) / AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
                val dlon =
                    mag * kotlin.math.sin(angle) /
                        (
                            AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE *
                                kotlin.math.cos(Math.toRadians(state.latitude))
                        )
                Pair(state.latitude + dlat, state.longitude + dlon)
            }

            else -> {
                Pair(state.latitude, state.longitude)
            }
        }

    // Accuracy with warm-up envelope
    val outAccuracy =
        if (state.warmupEnabled) {
            val elapsedSec = (nowMs - state.warmupStartMs) / 1000.0
            if (elapsedSec <= AppConstants.RealismConstants.WARMUP_DURATION_SECONDS) {
                val t = (elapsedSec / AppConstants.RealismConstants.WARMUP_DURATION_SECONDS).toFloat().coerceIn(0f, 1f)
                AppConstants.RealismConstants.WARMUP_INITIAL_ACCURACY_METERS +
                    t * (
                        AppConstants.LocationConstants.LOCATION_ACCURACY_FINE -
                            AppConstants.RealismConstants.WARMUP_INITIAL_ACCURACY_METERS
                    )
            } else {
                perturbAccuracy(AppConstants.LocationConstants.LOCATION_ACCURACY_FINE, random)
            }
        } else {
            perturbAccuracy(AppConstants.LocationConstants.LOCATION_ACCURACY_FINE, random)
        }

    return LocationFix(
        latitude = outLat,
        longitude = outLon,
        altitudeMeters = newAltitude,
        speedMs = state.speedMs,
        bearing = outBearing,
        accuracyMeters = outAccuracy,
        verticalAccuracyMeters = AppConstants.RealismConstants.VERTICAL_ACCURACY_METERS,
        bearingAccuracyDegrees = AppConstants.RealismConstants.BEARING_ACCURACY_DEGREES,
        speedAccuracyMps = AppConstants.RealismConstants.SPEED_ACCURACY_MPS,
        satelliteCount = if (state.satelliteExtrasEnabled) state.cachedSatelliteCount else null,
        usedInFixCount = if (state.satelliteExtrasEnabled) state.cachedUsedInFixCount else null,
    )
}

@AndroidEntryPoint
class MockLocationService : Service() {
    companion object {
        private const val TAG = "MockLocationService"
        private const val NOTIFICATION_ID = AppConstants.NotificationConstants.ID_ACTIVE
        private const val NOTIFICATION_ID_PERM_ERROR = AppConstants.NotificationConstants.ID_PERMISSION_ERROR
        private const val CHANNEL_ID = AppConstants.NotificationConstants.CHANNEL_ID_ACTIVE
        private const val CHANNEL_ID_PERM_ERROR = AppConstants.NotificationConstants.CHANNEL_ID_PERMISSION_ERROR

        private const val JOYSTICK_SERVICE_CLASS = AppConstants.ServiceConstants.JOYSTICK_SERVICE_CLASS
        private const val WIDGET_SERVICE_CLASS = AppConstants.ServiceConstants.WIDGET_SERVICE_CLASS

        const val ACTION_START = AppConstants.ServiceConstants.ACTION_START
        const val ACTION_STOP = AppConstants.ServiceConstants.ACTION_STOP
        const val ACTION_UPDATE_POSITION = AppConstants.ServiceConstants.ACTION_UPDATE_POSITION
        const val ACTION_ROUTE_REPLAY_START = AppConstants.ServiceConstants.ACTION_ROUTE_REPLAY_START
        const val ACTION_ROUTE_REPLAY_PAUSE = AppConstants.ServiceConstants.ACTION_ROUTE_REPLAY_PAUSE
        const val ACTION_ROUTE_REPLAY_RESUME = AppConstants.ServiceConstants.ACTION_ROUTE_REPLAY_RESUME
        const val ACTION_ROUTE_REPLAY_STOP = AppConstants.ServiceConstants.ACTION_ROUTE_REPLAY_STOP
        const val ACTION_ROUTE_REPLAY_CANCEL = AppConstants.ServiceConstants.ACTION_ROUTE_REPLAY_CANCEL
        const val ACTION_ROUTE_APPEND_WAYPOINT = AppConstants.ServiceConstants.ACTION_ROUTE_APPEND_WAYPOINT

        const val EXTRA_ROUTE_ID = AppConstants.ServiceConstants.EXTRA_ROUTE_ID
        const val EXTRA_IS_BACKWARD = AppConstants.ServiceConstants.EXTRA_IS_BACKWARD
        const val EXTRA_SPEED_MS = AppConstants.ServiceConstants.EXTRA_SPEED_MS
        const val EXTRA_WAYPOINT_LAT = AppConstants.ServiceConstants.EXTRA_WAYPOINT_LAT
        const val EXTRA_WAYPOINT_LON = AppConstants.ServiceConstants.EXTRA_WAYPOINT_LON
    }

    inner class LocalBinder : Binder() {
        fun getService(): MockLocationService = this@MockLocationService
    }

    private val binder = LocalBinder()

    @Inject lateinit var locationManager: LocationManager

    @Inject lateinit var locationRepository: LocationRepository

    @Inject lateinit var roamingRepository: RoamingRepository

    @Inject lateinit var routeRepository: RouteRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var routeReplayEngine: RouteReplayEngine

    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "MockLocationService coroutine crashed", throwable)
            _state.value = MockLocationState.ERROR
        }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    /** Guards concurrent access to updateJob to prevent double-start from rapid state emissions. */
    private val updateJobMutex = Mutex()

    @Volatile private var updateJob: Job? = null

    private val _state = MutableStateFlow(MockLocationState.IDLE)
    val state: StateFlow<MockLocationState> = _state.asStateFlow()

    @Volatile private var currentLat: Double = 0.0

    @Volatile private var currentLon: Double = 0.0

    @Volatile private var currentSpeedMs: Float = 0.0f

    @Volatile private var currentBearing: Float = 0.0f

    @Volatile private var jitterIdleRadiusMeters: Double = 0.0

    @Volatile private var jitterMovingRadiusMeters: Double = 1.0

    @Volatile private var jitterIntervalSeconds: Int = 3

    @Volatile private var lastJitterTimestampMs: Long = 0L

    @Volatile private var providerAdded = false

    // Realism setting flags (observed from SettingsRepository flows)
    @Volatile private var bearingHoldEnabled: Boolean =
        AppConstants.RealismConstants.BEARING_HOLD_ON_IDLE_DEFAULT

    @Volatile private var altitudeEnabled: Boolean =
        AppConstants.RealismConstants.ALTITUDE_ENABLED_DEFAULT

    @Volatile private var warmupEnabled: Boolean =
        AppConstants.RealismConstants.WARMUP_ENABLED_DEFAULT

    @Volatile private var satelliteExtrasEnabled: Boolean =
        AppConstants.RealismConstants.SATELLITE_EXTRAS_ENABLED_DEFAULT

    @Volatile private var suspendedMockingEnabled: Boolean =
        AppConstants.RealismConstants.SUSPENDED_MOCKING_ENABLED_DEFAULT

    // Per-tick realism state
    /** Bearing from the last tick where speedMs > 0; held when the device is stationary. */
    @Volatile private var lastNonZeroBearing: Float = 0f

    /** Seed altitude for the next tick's Gaussian walk; written back from LocationFix.altitudeMeters after each successful tick. */
    @Volatile private var currentAltitudeMeters: Double =
        AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS

    /** Wall-clock ms when startSpoofing() was called; NOT reset on pause/resume. */
    @Volatile private var spoofingStartMs: Long = 0L

    /** Same instant as spoofingStartMs; kept separate for semantic clarity. NOT reset on pause/resume. */
    @Volatile private var warmupStartMs: Long = 0L

    /** Timestamp of the most recent push/pause phase transition. */
    @Volatile private var suspendedPhaseStartMs: Long = 0L

    /** True while in the pause window of the push/pause cycle; buildLocation returns null during this phase. */
    @Volatile private var isSuspendedPhase: Boolean = false

    /** Timestamp of the last satellite count refresh; controls the slow-churn update cadence. */
    @Volatile private var lastSatelliteUpdateMs: Long = 0L

    /** Cached total visible satellite count; updated every SATELLITE_UPDATE_INTERVAL_MS. */
    @Volatile private var cachedSatelliteCount: Int = 0

    /** Cached satellites-used-in-fix count; updated alongside cachedSatelliteCount. */
    @Volatile private var cachedUsedInFixCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeLocationState()
    }

    private fun observeLocationState() {
        serviceScope.launch {
            _state.collect { state ->
                when (state) {
                    MockLocationState.RUNNING -> {
                        updateJobMutex.withLock {
                            if (updateJob == null) {
                                // Route replay drives its own position updates via the onPositionUpdate
                                // callback — starting a background loop here would cause duplicate pushes.
                                // Skip the loop when replay mode is already active.
                                val mode = locationRepository.currentMode.value
                                if (mode != MockMode.ROUTE_REPLAY) {
                                    setupTestProvider()
                                    startUpdateLoop()
                                    Log.i(TAG, "State changed to RUNNING - started update loop")
                                } else {
                                    setupTestProvider()
                                    Log.i(TAG, "State changed to RUNNING (route replay) - skipped update loop")
                                }
                            }
                        }
                        if (Settings.canDrawOverlays(this@MockLocationService)) {
                            startService(Intent().setClassName(packageName, JOYSTICK_SERVICE_CLASS))
                            startService(Intent().setClassName(packageName, WIDGET_SERVICE_CLASS))
                            Log.i(TAG, "Overlay services started")
                        } else {
                            Log.i(TAG, "SYSTEM_ALERT_WINDOW not granted — skipping overlay start")
                        }
                    }

                    MockLocationState.IDLE, MockLocationState.ERROR -> {
                        updateJobMutex.withLock {
                            if (updateJob != null) {
                                updateJob?.cancel()
                                updateJob = null
                                removeTestProvider()
                                Log.i(TAG, "State changed to IDLE/ERROR - stopped update loop")
                            }
                        }
                        stopService(Intent().setClassName(packageName, JOYSTICK_SERVICE_CLASS))
                        stopService(Intent().setClassName(packageName, WIDGET_SERVICE_CLASS))
                        Log.i(TAG, "Overlay services stopped")
                    }

                    MockLocationState.PAUSED -> {
                        updateJobMutex.withLock {
                            if (updateJob != null) {
                                updateJob?.cancel()
                                updateJob = null
                                Log.i(TAG, "State changed to PAUSED - paused update loop")
                            }
                        }
                        // Overlays remain visible during pause
                    }
                }
            }
        }
        serviceScope.launch {
            try {
                locationRepository.currentPosition.collect { position ->
                    if (position != null) {
                        currentLat = position.latitude
                        currentLon = position.longitude
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "currentPosition flow error", e)
            }
        }
        serviceScope.launch {
            settingsRepository
                .getJitterIdleRadius()
                .catch { e -> Log.e(TAG, "jitterIdleRadius flow error", e) }
                .collect { jitterIdleRadiusMeters = it }
        }
        serviceScope.launch {
            settingsRepository
                .getJitterMovingRadius()
                .catch { e -> Log.e(TAG, "jitterMovingRadius flow error", e) }
                .collect { jitterMovingRadiusMeters = it }
        }
        serviceScope.launch {
            settingsRepository
                .getJitterIntervalSeconds()
                .catch { e -> Log.e(TAG, "jitterIntervalSeconds flow error", e) }
                .collect { jitterIntervalSeconds = it }
        }
        // Realism toggle flows — each updates a @Volatile field consumed by captureSnapshot() → buildLocation().
        serviceScope.launch {
            settingsRepository
                .getRealismBearingHoldIdle()
                .catch { e -> Log.e(TAG, "bearingHoldEnabled flow error", e) }
                .collect { bearingHoldEnabled = it }
        }
        serviceScope.launch {
            settingsRepository
                .getRealismAltitudeEnabled()
                .catch { e -> Log.e(TAG, "altitudeEnabled flow error", e) }
                .collect { altitudeEnabled = it }
        }
        serviceScope.launch {
            settingsRepository
                .getRealismWarmupEnabled()
                .catch { e -> Log.e(TAG, "warmupEnabled flow error", e) }
                .collect { warmupEnabled = it }
        }
        serviceScope.launch {
            settingsRepository
                .getRealismSatelliteExtrasEnabled()
                .catch { e -> Log.e(TAG, "satelliteExtrasEnabled flow error", e) }
                .collect { satelliteExtrasEnabled = it }
        }
        serviceScope.launch {
            settingsRepository
                .getRealismSuspendedMockingEnabled()
                .catch { e -> Log.e(TAG, "suspendedMockingEnabled flow error", e) }
                .collect { suspendedMockingEnabled = it }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!hasLocationPermission()) {
            if (BuildConfig.DEBUG) {
                // In debug builds, allow the service to start without location permission so
                // spoofing can be iterated on without going through the full permission flow.
                // FOREGROUND_SERVICE_TYPE_LOCATION requires permission on API 34+, so fall back
                // to type 0 to avoid the SecurityException.
                Log.w(TAG, "DEBUG: location permission missing — starting foreground with no service type.")
                ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), 0)
            } else {
                Log.e(TAG, "Location permission not granted — posting error notification and resetting onboarding.")
                // Cannot call startForeground with FOREGROUND_SERVICE_TYPE_LOCATION without location
                // permission granted — doing so throws SecurityException on API 34+. Start foreground
                // with no service type as a fallback just long enough to post the error notification.
                ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), 0)
                postPermissionErrorNotification()
                serviceScope.launch { settingsRepository.setOnboardingComplete(false) }
                stopSelf()
                return START_NOT_STICKY
            }
        } else {
            // Permission is granted — safe to startForeground with FOREGROUND_SERVICE_TYPE_LOCATION.
            // Android enforces a 5-second window from onStartCommand, so this must come before
            // any other work.
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        }

        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(ServiceConstants.EXTRA_LAT, AppConstants.MapConstants.DEFAULT_LAT)
                val lon = intent.getDoubleExtra(ServiceConstants.EXTRA_LON, AppConstants.MapConstants.DEFAULT_LON)
                startSpoofing(lat, lon)
            }

            null -> {
                // Service restarted by OS (START_STICKY). Attempt to resume spoofing from the last
                // remembered position if the feature is enabled.
                serviceScope.launch {
                    val remember = settingsRepository.getRememberLastLocation().first()
                    val lastLoc = if (remember) settingsRepository.getLastLocation().first() else null
                    if (lastLoc != null) {
                        Log.i(TAG, "OS restart: resuming spoofing at remembered location ${lastLoc.latitude}, ${lastLoc.longitude}")
                        startSpoofing(lastLoc.latitude, lastLoc.longitude)
                    } else {
                        Log.i(TAG, "OS restart: no remembered location — staying idle")
                    }
                }
            }

            ACTION_STOP -> {
                stopSpoofing()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE_POSITION -> {
                val lat = intent?.getDoubleExtra(ServiceConstants.EXTRA_LAT, currentLat) ?: currentLat
                val lon = intent?.getDoubleExtra(ServiceConstants.EXTRA_LON, currentLon) ?: currentLon
                val speedMs = intent?.getFloatExtra(ServiceConstants.EXTRA_SPEED_MS, 0f) ?: 0f
                val bearing = intent?.getFloatExtra(ServiceConstants.EXTRA_BEARING, 0f) ?: 0f
                if (speedMs > 0f) {
                    updatePositionWithVector(lat, lon, speedMs, bearing)
                } else {
                    updatePosition(lat, lon)
                }
            }

            ACTION_ROUTE_REPLAY_START -> {
                val routeId = intent.getStringExtra(EXTRA_ROUTE_ID) ?: return START_STICKY
                val isBackward = intent.getBooleanExtra(EXTRA_IS_BACKWARD, false)
                val speedMs = intent.getDoubleExtra(EXTRA_SPEED_MS, AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS)
                handleReplayStart(routeId, isBackward, speedMs)
            }

            ACTION_ROUTE_REPLAY_PAUSE -> {
                handleReplayPause()
            }

            ACTION_ROUTE_REPLAY_RESUME -> {
                val speedMs = intent.getDoubleExtra(EXTRA_SPEED_MS, AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS)
                handleReplayResume(speedMs)
            }

            ACTION_ROUTE_REPLAY_STOP -> {
                serviceScope.launch { handleReplayStop() }
            }

            ACTION_ROUTE_REPLAY_CANCEL -> {
                serviceScope.launch { handleReplayCancel() }
            }

            ACTION_ROUTE_APPEND_WAYPOINT -> {
                val lat = intent.getDoubleExtra(EXTRA_WAYPOINT_LAT, 0.0)
                val lon = intent.getDoubleExtra(EXTRA_WAYPOINT_LON, 0.0)
                routeReplayEngine.appendWaypoint(LatLng(lat, lon))
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        // Don't call stopSpoofing() here — it calls stopSelf() which re-triggers onDestroy,
        // and the serviceScope.launch inside raced with the cancel below. Do cleanup directly.
        updateJob?.cancel()
        updateJob = null
        removeTestProvider()
        locationRepository.stopSpoofing()
        locationRepository.setActiveRouteId(null)
        routeReplayEngine.close()
        roamingRepository.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun startSpoofing(
        lat: Double,
        lon: Double,
    ) {
        if (_state.value == MockLocationState.RUNNING) {
            Log.i(TAG, "Spoofing already running; ignoring duplicate startSpoofing()")
            return
        }
        // Reset ERROR state so a retry attempt can proceed cleanly.
        if (_state.value == MockLocationState.ERROR) {
            Log.i(TAG, "Clearing ERROR state before retry")
            _state.value = MockLocationState.IDLE
        }

        currentLat = lat
        currentLon = lon
        currentSpeedMs = 0.0f
        currentBearing = 0.0f
        val now = SystemClock.elapsedRealtime()
        currentAltitudeMeters = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS
        spoofingStartMs = now
        warmupStartMs = now
        isSuspendedPhase = false
        suspendedPhaseStartMs = now
        locationRepository.setPositionInternal(LatLng(lat, lon))
        locationRepository.setMockMode(MockMode.TELEPORT)

        // Setting state to RUNNING triggers observeLocationState(), which calls
        // setupTestProvider() + startUpdateLoop(). Do NOT call them here to avoid
        // a double-invocation race.
        _state.value = MockLocationState.RUNNING
        locationRepository.startSpoofing()
        Log.i(TAG, "Spoofing started at ($lat, $lon)")
    }

    fun updatePosition(
        lat: Double,
        lon: Double,
    ) {
        currentLat = lat
        currentLon = lon
        locationRepository.setPositionInternal(LatLng(lat, lon))
    }

    // Joystick/walk callers: update locationRepository before calling this method (sets speed/bearing only).
    // Route replay uses onPositionUpdate lambda instead — does not call this method.
    fun updatePositionWithVector(
        lat: Double,
        lon: Double,
        speedMs: Float,
        bearing: Float,
    ) {
        currentLat = lat
        currentLon = lon
        currentSpeedMs = speedMs
        currentBearing = bearing
    }

    fun stopSpoofing() {
        updateJob?.cancel()
        updateJob = null
        removeTestProvider()
        _state.value = MockLocationState.IDLE
        locationRepository.setMockMode(MockMode.TELEPORT)
        locationRepository.stopSpoofing()
        locationRepository.setActiveRouteId(null)
        stopSelf()
        Log.i(TAG, "Spoofing stopped")
    }

    fun getCurrentPosition(): LatLng = LatLng(currentLat, currentLon)

    private fun handleReplayStart(
        routeId: String,
        isBackward: Boolean,
        speedMs: Double,
    ) {
        serviceScope.launch {
            // Ensure roaming is stopped before starting route replay — they must not run concurrently.
            if (locationRepository.currentMode.value == MockMode.ROAMING) {
                roamingRepository.stopRoaming()
            }
            val route = routeRepository.getRouteWithWaypoints(routeId).first() ?: return@launch
            if (route.waypoints.size < 2) return@launch

            val ordered = if (isBackward) route.waypoints.reversed() else route.waypoints
            val latLngs = ordered.map { it.position }
            val startPos = latLngs.first()

            currentSpeedMs = speedMs.toFloat()
            currentBearing = 0.0f

            // Set mode BEFORE state so the state observer sees ROUTE_REPLAY and
            // correctly skips starting the background update loop.
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
            locationRepository.setActiveRouteId(routeId)
            locationRepository.setIsReplayBackward(isBackward)
            locationRepository.setRouteWaypoints(latLngs)
            // Trigger RUNNING after mode is set.
            _state.value = MockLocationState.RUNNING
            locationRepository.startSpoofing()

            walkToPosition(startPos, speedMs)

            routeReplayEngine.start(
                waypoints = latLngs,
                speedMs = speedMs,
                isLooping = route.isLooping,
                onPositionUpdate = { pos ->
                    currentLat = pos.latitude
                    currentLon = pos.longitude
                    try {
                        locationRepository.setPositionInternal(pos)
                        pushLocationUpdate()
                    } catch (e: Exception) {
                        Log.e(TAG, "Position update failed during route replay", e)
                    }
                },
                onComplete = {
                    locationRepository.setRouteWaypoints(null)
                    locationRepository.setActiveRouteId(null)
                    locationRepository.setMockMode(MockMode.TELEPORT)
                },
            )

            // If pause was requested during walk-to-start (before engine launched),
            // ensure the engine is paused now that it has been initialized.
            if (_state.value == MockLocationState.PAUSED) {
                routeReplayEngine.pause()
            }
        }
    }

    private fun handleReplayPause() {
        routeReplayEngine.pause()
        updateJob?.cancel()
        updateJob = null
        _state.value = MockLocationState.PAUSED
        locationRepository.pauseSpoofing()
        Log.i(TAG, "Replay paused")
    }

    private fun handleReplayResume(speedMs: Double) {
        _state.value = MockLocationState.RUNNING
        locationRepository.startSpoofing()
        routeReplayEngine.resume(
            onPositionUpdate = { pos ->
                currentLat = pos.latitude
                currentLon = pos.longitude
                try {
                    locationRepository.setPositionInternal(pos)
                    pushLocationUpdate()
                } catch (e: Exception) {
                    Log.e(TAG, "Position update failed during route resume", e)
                }
            },
            onComplete = {
                locationRepository.setRouteWaypoints(null)
                stopSpoofing()
            },
        )
        Log.i(TAG, "Replay resumed at ${speedMs}m/s")
    }

    private suspend fun walkToPosition(
        target: LatLng,
        speedMs: Double,
    ) {
        val distancePerTick = speedMs * (AppConstants.LocationConstants.UPDATE_INTERVAL_MS / 1000.0)
        while (currentCoroutineContext().isActive) {
            val dist = haversineDistance(currentLat, currentLon, target.latitude, target.longitude)
            if (dist < AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS) break
            val bearing = calculateBearing(currentLat, currentLon, target.latitude, target.longitude)
            val step = minOf(distancePerTick, dist)
            val (newLat, newLon) = advancePosition(currentLat, currentLon, bearing, step)
            currentLat = newLat
            currentLon = newLon
            locationRepository.setPositionInternal(LatLng(newLat, newLon))
            pushLocationUpdate()
            delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
        }
    }

    private suspend fun handleReplayStop() {
        locationRepository.setRouteWaypoints(null)
        routeReplayEngine.stop()
        stopSpoofing()
    }

    private suspend fun handleReplayCancel() {
        locationRepository.setRouteWaypoints(null)
        routeReplayEngine.stop()
        locationRepository.setMockMode(MockMode.TELEPORT)
        locationRepository.setActiveRouteId(null)
        // Only restart the idle update loop if spoofing is still active.
        // If spoofing was stopped concurrently, do not start a new loop.
        if (_state.value == MockLocationState.RUNNING) {
            startUpdateLoop()
        }
        Log.i(TAG, "Replay cancelled; service remains active in TELEPORT mode")
    }

    private fun setupTestProvider() {
        if (providerAdded) return
        // Attempt to remove any ghost provider left by a previous crash. If the process was killed
        // mid-run the provider may still be registered under our package, causing addTestProvider
        // to throw IllegalArgumentException. Removing first clears the slot safely.
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            Log.i(TAG, "Removed stale test provider before re-registering")
        } catch (_: IllegalArgumentException) {
            // Not registered — nothing to clear
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error clearing stale test provider", e)
        }

        try {
            val properties =
                ProviderProperties
                    .Builder()
                    .setHasAltitudeSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_HIGH)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .build()

            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                properties,
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            providerAdded = true
            Log.i(TAG, "Test provider registered")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to add test provider — another app may hold the slot", e)
            _state.value = MockLocationState.ERROR
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing mock location permission", e)
            _state.value = MockLocationState.ERROR
        }
    }

    private fun removeTestProvider() {
        if (!providerAdded) return
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            providerAdded = false
            Log.i(TAG, "Test provider removed")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Test provider was already removed", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error removing test provider", e)
        }
    }

    private fun startUpdateLoop() {
        updateJob?.cancel()
        updateJob =
            serviceScope.launch {
                while (isActive) {
                    pushLocationUpdate()
                    delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                }
            }
    }

    /**
     * Advances the push/pause phase state machine before each tick.
     *
     * Mutates [isSuspendedPhase] and [suspendedPhaseStartMs] (@Volatile fields). Must be called
     * before [captureSnapshot] so the snapshot reflects the current phase for [buildLocation].
     * No-ops when suspended mocking is disabled or when route replay / walk-to is active (those
     * modes must not drop ticks).
     */
    private fun updateSuspendedPhase() {
        val now = SystemClock.elapsedRealtime()
        val mode = locationRepository.currentMode.value
        if (!suspendedMockingEnabled || mode == MockMode.ROUTE_REPLAY || mode == MockMode.WALK_TO) {
            isSuspendedPhase = false
            return
        }
        val elapsed = now - suspendedPhaseStartMs
        if (!isSuspendedPhase && elapsed >= AppConstants.RealismConstants.SUSPENDED_PUSH_DURATION_MS) {
            isSuspendedPhase = true
            suspendedPhaseStartMs = now
            Log.i(TAG, "Realism: suspended phase=PAUSED dur=${elapsed}ms")
        } else if (isSuspendedPhase) {
            val pauseDur =
                AppConstants.RealismConstants.SUSPENDED_PAUSE_DURATION_MS +
                    Random.nextLong(0, AppConstants.RealismConstants.SUSPENDED_PAUSE_JITTER_MS)
            if (elapsed >= pauseDur) {
                isSuspendedPhase = false
                suspendedPhaseStartMs = now
                Log.i(TAG, "Realism: suspended phase=PUSHING dur=${elapsed}ms")
            }
        }
    }

    /**
     * Freezes all @Volatile fields into an immutable [LocationSnapshot] to eliminate TOCTOU races
     * in the tick loop. Also computes [LocationSnapshot.shouldApplyMovingJitter] and refreshes the
     * slow-churn satellite counts when their interval has elapsed.
     */
    private fun captureSnapshot(nowMs: Long): LocationSnapshot {
        val mode = locationRepository.currentMode.value
        val jitterIntervalMs = jitterIntervalSeconds * 1000L
        val shouldApplyMovingJitter = (nowMs - lastJitterTimestampMs) >= jitterIntervalMs

        // Slow satellite churn
        if (satelliteExtrasEnabled &&
            (nowMs - lastSatelliteUpdateMs) >= AppConstants.RealismConstants.SATELLITE_UPDATE_INTERVAL_MS
        ) {
            cachedSatelliteCount =
                Random.nextInt(
                    AppConstants.RealismConstants.SATELLITES_MIN,
                    AppConstants.RealismConstants.SATELLITES_MAX + 1,
                )
            cachedUsedInFixCount =
                Random.nextInt(
                    AppConstants.RealismConstants.USED_IN_FIX_MIN,
                    minOf(AppConstants.RealismConstants.USED_IN_FIX_MAX + 1, cachedSatelliteCount + 1),
                )
            lastSatelliteUpdateMs = nowMs
        }

        return LocationSnapshot(
            latitude = currentLat,
            longitude = currentLon,
            speedMs = currentSpeedMs,
            bearing = currentBearing,
            lastNonZeroBearing = lastNonZeroBearing,
            mode = mode,
            jitterIdleRadiusMeters = jitterIdleRadiusMeters,
            jitterMovingRadiusMeters = jitterMovingRadiusMeters,
            shouldApplyMovingJitter = shouldApplyMovingJitter,
            altitudeMeters = currentAltitudeMeters,
            warmupStartMs = warmupStartMs,
            spoofingStartMs = spoofingStartMs,
            warmupEnabled = warmupEnabled,
            bearingHoldEnabled = bearingHoldEnabled,
            altitudeEnabled = altitudeEnabled,
            satelliteExtrasEnabled = satelliteExtrasEnabled,
            suspendedMockingEnabled = suspendedMockingEnabled,
            suspendedPhaseStartMs = suspendedPhaseStartMs,
            isSuspendedPhase = isSuspendedPhase,
            cachedSatelliteCount = cachedSatelliteCount,
            cachedUsedInFixCount = cachedUsedInFixCount,
        )
    }

    /**
     * Android adapter — the only place that touches [android.location.Location] APIs. Translates
     * the pure [LocationFix] into a test provider location update.
     */
    private fun applyToProvider(fix: LocationFix) {
        val loc =
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = fix.latitude
                longitude = fix.longitude
                altitude = fix.altitudeMeters
                accuracy = fix.accuracyMeters
                speed = fix.speedMs
                bearing = fix.bearing
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                verticalAccuracyMeters = fix.verticalAccuracyMeters
                bearingAccuracyDegrees = fix.bearingAccuracyDegrees
                speedAccuracyMetersPerSecond = fix.speedAccuracyMps
                if (fix.satelliteCount != null && fix.usedInFixCount != null) {
                    val extras = android.os.Bundle()
                    extras.putInt("satellites", fix.satelliteCount)
                    extras.putInt("usedInFix", fix.usedInFixCount)
                    this.extras = extras
                }
            }
        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)
    }

    private fun pushLocationUpdate() {
        if (!providerAdded) return
        try {
            updateSuspendedPhase()
            val nowMs = SystemClock.elapsedRealtime()
            val snapshot = captureSnapshot(nowMs)
            val fix = buildLocation(snapshot, nowMs, Random.Default) ?: return
            currentAltitudeMeters = fix.altitudeMeters
            if (snapshot.speedMs > 0f) lastNonZeroBearing = snapshot.bearing
            if (snapshot.shouldApplyMovingJitter && snapshot.mode != MockMode.TELEPORT) {
                lastJitterTimestampMs = nowMs
            }
            applyToProvider(fix)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to push location update", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error pushing location update", e)
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                AppConstants.NotificationConstants.CHANNEL_NAME_ACTIVE,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = AppConstants.NotificationConstants.CHANNEL_DESC_ACTIVE
                setShowBadge(false)
            }
        val errorChannel =
            NotificationChannel(
                CHANNEL_ID_PERM_ERROR,
                AppConstants.NotificationConstants.CHANNEL_NAME_PERMISSION_ERROR,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = AppConstants.NotificationConstants.CHANNEL_DESC_PERMISSION_ERROR
            }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(errorChannel)
    }

    private fun postPermissionErrorNotification() {
        val openAppIntent =
            packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID_PERM_ERROR)
                .setContentTitle(AppConstants.NotificationConstants.TITLE_PERMISSION_ERROR)
                .setContentText(AppConstants.NotificationConstants.TEXT_PERMISSION_ERROR)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(openAppIntent)
                .setAutoCancel(true)
                .build()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_PERM_ERROR, notification)
    }

    private fun buildNotification(): Notification {
        val openAppIntent =
            packageManager
                .getLaunchIntentForPackage(packageName)
                ?.let { intent ->
                    PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                }

        val stopIntent =
            Intent(this, MockLocationService::class.java).apply {
                action = ACTION_STOP
            }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(AppConstants.NotificationConstants.TITLE_ACTIVE)
            .setContentText(AppConstants.NotificationConstants.TEXT_ACTIVE)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_delete,
                AppConstants.NotificationConstants.ACTION_STOP,
                stopPendingIntent,
            ).setOngoing(true)
            .setSilent(true)
            .build()
    }
}
