package com.locationjoystick.core.location

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.constants.AppConstants.ServiceConstants
import com.locationjoystick.core.common.util.NetworkUtils
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.data.DebugStats
import com.locationjoystick.core.data.ElevationRepository
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.WalkToEngine
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.SyncPositionUpdate
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RouteReplayEngine
import com.locationjoystick.core.routing.RoutingErrorReporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class MockLocationService : Service() {
    companion object {
        private const val TAG = "MockLocationService"
        private const val NOTIFICATION_ID = AppConstants.NotificationConstants.ID_ACTIVE

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
        const val ACTION_ROUTE_REPLAY_JUMP_NEXT = AppConstants.ServiceConstants.ACTION_ROUTE_REPLAY_JUMP_NEXT
        const val ACTION_ROUTE_REPLAY_JUMP_PREVIOUS = AppConstants.ServiceConstants.ACTION_ROUTE_REPLAY_JUMP_PREVIOUS
        const val ACTION_ROUTE_APPEND_WAYPOINT = AppConstants.ServiceConstants.ACTION_ROUTE_APPEND_WAYPOINT
        const val ACTION_ENTER_FOLLOWER = AppConstants.ServiceConstants.ACTION_ENTER_FOLLOWER
        const val ACTION_EXIT_FOLLOWER = AppConstants.ServiceConstants.ACTION_EXIT_FOLLOWER
        const val ACTION_FOLLOWER_TELEPORT = AppConstants.ServiceConstants.ACTION_FOLLOWER_TELEPORT

        const val EXTRA_ROUTE_ID = AppConstants.ServiceConstants.EXTRA_ROUTE_ID
        const val EXTRA_IS_BACKWARD = AppConstants.ServiceConstants.EXTRA_IS_BACKWARD
        const val EXTRA_SPEED_MS = AppConstants.ServiceConstants.EXTRA_SPEED_MS
        const val EXTRA_WAYPOINT_LAT = AppConstants.ServiceConstants.EXTRA_WAYPOINT_LAT
        const val EXTRA_WAYPOINT_LON = AppConstants.ServiceConstants.EXTRA_WAYPOINT_LON
        const val EXTRA_IS_EPHEMERAL = AppConstants.ServiceConstants.EXTRA_IS_EPHEMERAL
        const val EXTRA_IS_LOOPING = AppConstants.ServiceConstants.EXTRA_IS_LOOPING
        const val EXTRA_RETURN_LAT = AppConstants.ServiceConstants.EXTRA_RETURN_LAT
        const val EXTRA_RETURN_LON = AppConstants.ServiceConstants.EXTRA_RETURN_LON
        const val EXTRA_FOLLOW_ROADS_TO_START = AppConstants.ServiceConstants.EXTRA_FOLLOW_ROADS_TO_START
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

    @Inject lateinit var walkToEngine: WalkToEngine

    @Inject lateinit var osrmClient: OsrmClient

    @Inject lateinit var routingErrorReporter: RoutingErrorReporter

    @Inject lateinit var leaderSyncServer: LeaderSyncServer

    @Inject lateinit var followerSyncClient: FollowerSyncClient

    @Inject lateinit var groupRepository: GroupRepository

    @Inject lateinit var groupNsdManager: NsdCodeManager

    @Inject lateinit var elevationRepository: ElevationRepository

    private val notificationManager: android.app.NotificationManager by lazy {
        getSystemService(android.app.NotificationManager::class.java)
    }

    private val powerManager: PowerManager by lazy {
        getSystemService(PowerManager::class.java)
    }

    /** Held only while actively spoofing (RUNNING/PAUSED) — works around Doze/Adaptive Battery
     * throttling the update loop on some devices (e.g. Android 15 Pixel) when the screen locks. */
    @Volatile private var wakeLock: PowerManager.WakeLock? = null

    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "MockLocationService coroutine crashed", throwable)
            _state.value = MockLocationState.ERROR
        }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    private lateinit var replayOrchestrator: ReplayOrchestrator

    private lateinit var overlayNotificationReactor: OverlayNotificationReactor

    /** Guards concurrent access to updateJob to prevent double-start from rapid state emissions. */
    private val updateJobMutex = Mutex()

    @Volatile private var updateJob: Job? = null

    /** Async job for follower restoration with NSD discovery retries on device boot. */
    private var followerRestorationJob: Job? = null

    private val _state = MutableStateFlow(MockLocationState.IDLE)
    val state: StateFlow<MockLocationState> = _state.asStateFlow()

    private val positionRef = AtomicReference(LatLng(0.0, 0.0))

    internal val followerCatchUp = FollowerCatchUpCoordinator()

    @Volatile private var currentSpeedMs: Float = 0.0f

    @Volatile private var currentBearing: Float = 0.0f

    @Volatile private var lastJitterTimestampMs: Long = 0L

    @Volatile private var lastIdleJitterTimestampMs: Long = 0L

    @Volatile private var providerAdded = false

    /** Realism settings observed from [SettingsRepository] and read each tick in [captureSnapshot]. */
    private val realism = RealismSettingsState()

    @Volatile private var humanAltitudeOffsetMeters: Double = AppConstants.RealismConstants.ALTITUDE_HUMAN_OFFSET_METERS

    @Volatile private var leaderSharingEnabled: Boolean = false

    // Per-tick realism state

    /** Bearing from the last tick where speedMs > 0; held when the device is stationary. */
    @Volatile private var lastNonZeroBearing: Float = 0f

    /** Seed altitude for the next tick's Gaussian walk; written back from LocationFix.altitudeMeters after each successful tick. */
    @Volatile private var currentAltitudeMeters: Double =
        AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS

    /** Clamp-center anchor for the altitude Gaussian walk; converges toward [targetBaseAltitudeMeters] each tick. */
    @Volatile private var currentBaseAltitudeMeters: Double = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS

    /** Where [currentBaseAltitudeMeters] is converging to — set by a real-elevation fetch or a manual override. */
    @Volatile private var targetBaseAltitudeMeters: Double = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS

    /** Wall-clock ms of the last real-elevation fetch attempt; gates [maybeFetchElevation]'s interval check. */
    @Volatile private var lastElevationFetchMs: Long = 0L

    /** Guards against stacking a new elevation fetch while one is still in flight. */
    @Volatile private var elevationFetchInFlight: Boolean = false

    /** Wall-clock ms when startSpoofing() was called; NOT reset on pause/resume. */
    @Volatile private var warmupStartMs: Long = 0L

    /** Atomically-updated push/pause phase state; avoids torn reads between isActive and startMs. */
    private val suspendedPhase = AtomicReference(SuspendedPhaseState(isActive = false, startMs = 0L))

    /** Timestamp of the last satellite count refresh; controls the slow-churn update cadence. */
    @Volatile private var lastSatelliteUpdateMs: Long = 0L

    /** Cached total visible satellite count; updated every SATELLITE_UPDATE_INTERVAL_MS. */
    @Volatile private var cachedSatelliteCount: Int = 0

    /** Cached satellites-used-in-fix count; updated alongside cachedSatelliteCount. */
    @Volatile private var cachedUsedInFixCount: Int = 0

    /** Timestamp of the last live-position DataStore write; throttles persistLastLocation(). */
    @Volatile private var lastPersistedLocationMs: Long = 0L

    /** Timestamp of the previous tick; used to compute the debug overlay's tick interval. */
    @Volatile private var lastTickMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        overlayNotificationReactor =
            OverlayNotificationReactor(
                context = this,
                notificationManager = notificationManager,
                locationRepository = locationRepository,
                settingsRepository = settingsRepository,
            )
        replayOrchestrator =
            ReplayOrchestrator(
                locationRepository = locationRepository,
                routeRepository = routeRepository,
                roamingRepository = roamingRepository,
                routeReplayEngine = routeReplayEngine,
                walkToEngine = walkToEngine,
                osrmClient = osrmClient,
                routingErrorReporter = routingErrorReporter,
                scope = serviceScope,
                onStateChange = { _state.value = it },
                onPositionChange = { lat, lon ->
                    writeCurrentPosition(lat, lon)
                },
                onSpeedChange = { currentSpeedMs = it },
                pushLocationUpdate = ::pushLocationUpdate,
                startUpdateLoop = ::startUpdateLoop,
            )
        createNotificationChannel()
        observeLocationState()
        observeGroupState()
    }

    private fun observeLocationState() {
        serviceScope.launch {
            _state.collect { state ->
                when (state) {
                    MockLocationState.RUNNING -> {
                        updateJobMutex.withLock {
                            // Route replay drives its own position updates via the onPositionUpdate
                            // callback. If the idle loop was kept alive to push the frozen position
                            // while PAUSED, stop it here so a resumed replay doesn't double-push.
                            val mode = locationRepository.currentMode.value
                            if (mode == MockMode.ROUTE_REPLAY) {
                                updateJob?.cancel()
                                updateJob = null
                                setupTestProvider()
                                Log.i(TAG, "State changed to RUNNING (route replay) - stopped idle loop")
                            } else if (updateJob == null) {
                                setupTestProvider()
                                startUpdateLoop()
                                Log.i(TAG, "State changed to RUNNING - started update loop")
                            }
                        }
                        if (Settings.canDrawOverlays(this@MockLocationService)) {
                            // Widget overlay is started by the reactive collector below, which also
                            // reacts to getHideWidgetOverlay() flipping mid-session, not just here.
                            startService(Intent().setClassName(packageName, JOYSTICK_SERVICE_CLASS))
                            Log.i(TAG, "Overlay services started")
                        } else {
                            Log.i(TAG, "SYSTEM_ALERT_WINDOW not granted — skipping overlay start")
                        }
                    }

                    MockLocationState.IDLE, MockLocationState.ERROR -> {
                        updateJobMutex.withLock {
                            when (computeIdleOrErrorLoopAction(state, leaderSharingEnabled, updateJob != null)) {
                                IdleOrErrorLoopAction.KEEP_ALIVE -> {
                                    Log.i(
                                        TAG,
                                        "State changed to $state - leader sharing active, keeping test provider alive",
                                    )
                                }

                                IdleOrErrorLoopAction.TEAR_DOWN -> {
                                    updateJob?.cancel()
                                    updateJob = null
                                    removeTestProvider()
                                    Log.i(TAG, "State changed to $state - stopped update loop")
                                }

                                IdleOrErrorLoopAction.NO_OP -> {
                                    Unit
                                }
                            }
                        }
                        stopService(Intent().setClassName(packageName, JOYSTICK_SERVICE_CLASS))
                        stopService(Intent().setClassName(packageName, WIDGET_SERVICE_CLASS))
                        Log.i(TAG, "Overlay services stopped")
                    }

                    MockLocationState.PAUSED -> {
                        updateJobMutex.withLock {
                            when (computePausedLoopAction(updateJob != null)) {
                                PausedLoopAction.START_UP -> {
                                    startUpdateLoop()
                                    Log.i(TAG, "State changed to PAUSED - started idle loop to keep position frozen")
                                }

                                PausedLoopAction.KEEP_ALIVE -> {
                                    Log.i(TAG, "State changed to PAUSED - idle loop already alive")
                                }
                            }
                        }
                        // Overlays remain visible during pause
                    }
                }
            }
        }
        serviceScope.launch {
            locationRepository.currentPosition.collect { position ->
                if (position != null) {
                    writeCurrentPosition(position.latitude, position.longitude)
                }
            }
        }
        serviceScope.launch {
            locationRepository.currentBearing.collect { bearing ->
                if (bearing != null) currentBearing = bearing
            }
        }
        // Applies a manual altitude override (widget button) to a running session immediately —
        // DataStore write -> Flow emission -> this collector — with no Intent/service command
        // needed. Skipped when no override exists yet (null -> no-op) so it never fights
        // startSpoofing()'s own resolution order.
        serviceScope.launch {
            settingsRepository.getBaseAltitudeOverride().collect { override ->
                if (override != null) {
                    currentBaseAltitudeMeters = override
                    targetBaseAltitudeMeters = override
                    locationRepository.setReportedAltitude(override)
                }
            }
        }
        // Keeps the foreground notification and widget overlay in sync with live setting
        // changes mid-session (see OverlayNotificationReactor's class doc for why).
        overlayNotificationReactor.observe(serviceScope, _state)
        // Jitter and realism settings — each updates a @Volatile field consumed by captureSnapshot().
        realism.observe(serviceScope, settingsRepository)
        // Propagate live speed-profile changes (e.g. widget Speed Cycle) into an active route
        // replay or roaming walk — both engines otherwise freeze speedMs at the value passed
        // to start().
        serviceScope.launch {
            settingsRepository.getActiveSpeedProfile().collect { profile ->
                when (locationRepository.currentMode.value) {
                    MockMode.ROUTE_REPLAY -> {
                        val activeRouteId = locationRepository.activeRouteId.value
                        val isSpeedLocked =
                            activeRouteId != null &&
                                routeRepository.getRouteWithWaypoints(activeRouteId).first()?.speedProfileId != null
                        if (!isSpeedLocked) {
                            replayOrchestrator.updateSpeed(profile.speedMetersPerSecond)
                        }
                    }

                    MockMode.ROAMING -> {
                        roamingRepository.updateSpeed(profile.speedMetersPerSecond)
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
    }

    /**
     * Single write entry point for position. All position writers funnel through here so the
     * latitude/longitude pair is always written atomically, eliminating torn reads between a
     * mid-tick teleport and [updatePositionWithVector].
     *
     * [syncRepository] also pushes the same position to [LocationRepository] (consumed by the map
     * UI) in the same call — pass `true` for any mode that has no other writer of its own already
     * updating the repository (e.g. teleport, follower catch-up). Leave `false` when a caller
     * already syncs the repository separately (e.g. [ReplayOrchestrator]) or is itself mirroring
     * a repository change back into [positionRef], where syncing here would be circular.
     */
    private fun writeCurrentPosition(
        lat: Double,
        lon: Double,
        syncRepository: Boolean = false,
    ) {
        positionRef.set(LatLng(lat, lon))
        if (syncRepository) {
            locationRepository.setPositionInternal(LatLng(lat, lon))
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
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(hideNotification = overlayNotificationReactor.hideNotificationSetting),
                    0,
                )
            } else {
                Log.e(TAG, "Location permission not granted — posting error notification and resetting onboarding.")
                // Cannot call startForeground with FOREGROUND_SERVICE_TYPE_LOCATION without location
                // permission granted — doing so throws SecurityException on API 34+. Start foreground
                // with no service type as a fallback just long enough to post the error notification.
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(hideNotification = overlayNotificationReactor.hideNotificationSetting),
                    0,
                )
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
                buildNotification(hideNotification = overlayNotificationReactor.hideNotificationSetting),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        }

        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(ServiceConstants.EXTRA_LAT, AppConstants.MapConstants.DEFAULT_LAT)
                val lon = intent.getDoubleExtra(ServiceConstants.EXTRA_LON, AppConstants.MapConstants.DEFAULT_LON)
                startSpoofing(lat, lon)
            }

            ACTION_ENTER_FOLLOWER -> {
                val host = intent.getStringExtra(AppConstants.ServiceConstants.EXTRA_FOLLOWER_HOST) ?: return START_STICKY
                val port = intent.getIntExtra(AppConstants.ServiceConstants.EXTRA_FOLLOWER_PORT, 0)
                val groupId = intent.getStringExtra(AppConstants.ServiceConstants.EXTRA_FOLLOWER_GROUP_ID) ?: return START_STICKY
                enterFollowerMode(host, port, groupId)
            }

            ACTION_EXIT_FOLLOWER -> {
                exitFollowerMode()
            }

            AppConstants.ServiceConstants.ACTION_START_LEADER -> {
                val groupId = intent.getStringExtra(AppConstants.ServiceConstants.EXTRA_LEADER_GROUP_ID) ?: return START_STICKY
                enterLeaderMode(groupId)
            }

            AppConstants.ServiceConstants.ACTION_EXIT_LEADER -> {
                exitLeaderMode()
            }

            null -> {
                // Service restarted by OS (START_STICKY). Resume leader/follower mode first if active,
                // else fall through to remembered-location logic.
                serviceScope.launch {
                    val groupState = groupRepository.groupState.first()
                    if (groupState.role == GroupRole.LEADER) {
                        val id = groupState.groupId ?: return@launch
                        Log.i(TAG, "OS restart: resuming leader mode for group $id")
                        enterLeaderMode(id)
                        return@launch
                    }
                    if (groupState.role == GroupRole.FOLLOWER && groupState.followerModeEnabled) {
                        val id = groupState.groupId ?: return@launch
                        // Leader may have restarted with a new OS-assigned port — start async restoration
                        // with retries rather than a synchronous NSD call that may timeout if the leader
                        // hasn't advertised yet. Don't block onStartCommand.
                        Log.i(TAG, "OS restart: starting async follower restoration for group $id")
                        startFollowerRestoration(id)
                        return@launch
                    }
                    val remember = settingsRepository.getRememberLastLocation().first()
                    val lastLoc = if (remember) settingsRepository.getLastLocation().first() else null
                    if (lastLoc != null) {
                        Log.i(
                            TAG,
                            "OS restart: resuming spoofing at remembered location ${lastLoc.latitude}, ${lastLoc.longitude}",
                        )
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
                val currentPos = positionRef.get()
                val lat = intent.getDoubleExtra(ServiceConstants.EXTRA_LAT, currentPos.latitude)
                val lon = intent.getDoubleExtra(ServiceConstants.EXTRA_LON, currentPos.longitude)
                val speedMs = intent.getFloatExtra(ServiceConstants.EXTRA_SPEED_MS, 0f)
                val bearing = intent.getFloatExtra(ServiceConstants.EXTRA_BEARING, 0f)
                if (speedMs > 0f) {
                    updatePositionWithVector(lat, lon, speedMs, bearing)
                } else {
                    updatePosition(lat, lon)
                }
            }

            ACTION_ROUTE_REPLAY_START -> {
                val isEphemeral = intent.getBooleanExtra(EXTRA_IS_EPHEMERAL, false)
                val speedMs =
                    intent.getDoubleExtra(
                        EXTRA_SPEED_MS,
                        AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS,
                    )
                if (isEphemeral) {
                    val encoded = intent.getStringExtra(AppConstants.ServiceConstants.EXTRA_EPHEMERAL_WAYPOINTS)
                    val waypoints =
                        encoded
                            ?.split(";")
                            ?.mapNotNull { seg ->
                                val parts = seg.split(",")
                                val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                                val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                                LatLng(lat, lon)
                            }?.takeIf { it.size >= 2 }
                            ?: return START_STICKY
                    handleEphemeralReplayStart(waypoints, speedMs)
                } else {
                    val routeId = intent.getStringExtra(EXTRA_ROUTE_ID) ?: return START_STICKY
                    val isBackward = intent.getBooleanExtra(EXTRA_IS_BACKWARD, false)
                    val isLoopingOverride =
                        if (intent.hasExtra(EXTRA_IS_LOOPING)) intent.getBooleanExtra(EXTRA_IS_LOOPING, false) else null
                    val returnLat = intent.getDoubleExtra(EXTRA_RETURN_LAT, Double.NaN)
                    val returnLon = intent.getDoubleExtra(EXTRA_RETURN_LON, Double.NaN)
                    val returnPosition =
                        if (!returnLat.isNaN() && !returnLon.isNaN()) LatLng(returnLat, returnLon) else null
                    val followRoadsToStart = intent.getBooleanExtra(EXTRA_FOLLOW_ROADS_TO_START, false)
                    handleReplayStart(routeId, isBackward, speedMs, isLoopingOverride, returnPosition, followRoadsToStart)
                }
            }

            ACTION_ROUTE_REPLAY_PAUSE -> {
                handleReplayPause()
            }

            ACTION_ROUTE_REPLAY_RESUME -> {
                val speedMs =
                    intent.getDoubleExtra(
                        EXTRA_SPEED_MS,
                        AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS,
                    )
                handleReplayResume(speedMs)
            }

            ACTION_ROUTE_REPLAY_STOP -> {
                serviceScope.launch { handleReplayStop() }
            }

            ACTION_ROUTE_REPLAY_CANCEL -> {
                serviceScope.launch { handleReplayCancel() }
            }

            ACTION_ROUTE_REPLAY_JUMP_NEXT -> {
                handleReplayJumpNext()
            }

            ACTION_ROUTE_REPLAY_JUMP_PREVIOUS -> {
                handleReplayJumpPrevious()
            }

            ACTION_FOLLOWER_TELEPORT -> {
                teleportToLeaderNow()
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
        // Don't call stopSpoofing() here — it calls stopSelf() which re-triggers onDestroy.
        // serviceScope.cancel() below cancels updateJob (child of the scope) automatically.
        updateJob = null
        removeTestProvider()
        releaseWakeLock()
        // Persist last location on external kill (process killed, OOM, etc.)
        // stopSpoofing() handles this on normal stop, but onDestroy may be called without it.
        // Always persist location; the rememberLastLocation setting only controls whether to restore it.
        if (_state.value == MockLocationState.RUNNING) {
            val pos = positionRef.get()
            CoroutineScope(NonCancellable + Dispatchers.IO).launch { settingsRepository.setLastLocation(pos) }
        }
        stopFollowerRestoration()
        leaderSyncServer.stop()
        followerSyncClient.stopPolling()
        locationRepository.stopSpoofing()
        locationRepository.setActiveRouteId(null)
        routeReplayEngine.cancelActiveReplay()
        roamingRepository.resetOnServiceDestroy()
        // RoamingEngine.close() (which cancels engineScope) is not called here because RoamingEngine
        // is not injected in this service. resetOnServiceDestroy() cancels the active job via stop();
        // any further orphan cleanup happens at process death when the JVM GC collects the singleton.
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

        writeCurrentPosition(lat, lon, syncRepository = true)
        currentSpeedMs = 0.0f
        currentBearing = 0.0f
        val now = SystemClock.elapsedRealtime()
        currentAltitudeMeters = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS
        currentBaseAltitudeMeters = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS
        targetBaseAltitudeMeters = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS
        // Resolves async — a manual override is re-applied instantly anyway via the reactive
        // collector in observeLocationState(), and maybeFetchElevation() re-checks the override
        // before every fetch, so the sliver of a window before this completes is harmless.
        serviceScope.launch {
            settingsRepository.getBaseAltitudeOverride().first()?.let { override ->
                currentBaseAltitudeMeters = override
                targetBaseAltitudeMeters = override
            }
        }
        maybeFetchElevation(now, lat, lon)
        warmupStartMs = now
        suspendedPhase.set(SuspendedPhaseState(isActive = false, startMs = now))
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
        lastSatelliteUpdateMs = now
        lastJitterTimestampMs = now
        lastIdleJitterTimestampMs = now
        locationRepository.setMockMode(MockMode.TELEPORT)

        // Setting state to RUNNING triggers observeLocationState(), which calls
        // setupTestProvider() + startUpdateLoop(). Do NOT call them here to avoid
        // a double-invocation race.
        acquireWakeLock()
        _state.value = MockLocationState.RUNNING
        locationRepository.startSpoofing()
        Log.i(TAG, "Spoofing started at ($lat, $lon)")
    }

    private fun acquireWakeLock() {
        wakeLock = acquireSpoofingWakeLock(powerManager)
    }

    private fun releaseWakeLock() {
        releaseSpoofingWakeLock(wakeLock)
        wakeLock = null
    }

    fun updatePosition(
        lat: Double,
        lon: Double,
    ) {
        if (locationRepository.currentMode.value == MockMode.FOLLOWER) return
        writeCurrentPosition(lat, lon, syncRepository = true)
    }

    // Callers: JoystickOverlayService (direct call) and the WalkCoordinator position callback
    // (ACTION_UPDATE_POSITION intent) — both self-report their own mode (JOYSTICK / WALK_TO).
    // Every other mode's engine (ReplayOrchestrator, RoamingEngine, follower sync) owns position
    // updates for its own tick and must not have them overwritten by a stale/racing call here.
    fun updatePositionWithVector(
        lat: Double,
        lon: Double,
        speedMs: Float,
        bearing: Float,
    ) {
        val mode = locationRepository.currentMode.value
        if (mode != MockMode.JOYSTICK && mode != MockMode.WALK_TO) return
        writeCurrentPosition(lat, lon)
        currentSpeedMs = speedMs
        currentBearing = bearing
    }

    // Callers: JoystickOverlayService, on unlock/release — clears the stale motion vector left by
    // updatePositionWithVector so realism ticks settle into idle jitter instead of re-randomizing
    // around the frozen position as if still moving.
    fun clearMotionVector() {
        currentSpeedMs = 0.0f
        currentBearing = 0.0f
    }

    fun stopSpoofing() {
        // Cancel immediately (idempotent); null assignment deferred under mutex so the
        // RUNNING observer can't start a new loop between our cancel and the null write.
        updateJob?.cancel()
        serviceScope.launch { updateJobMutex.withLock { updateJob = null } }
        if (roamingRepository.isRoaming.value) {
            serviceScope.launch { roamingRepository.stopRoaming() }
        }
        // Always persist location; the rememberLastLocation setting only controls whether to restore it.
        val pos = positionRef.get()
        serviceScope.launch { settingsRepository.setLastLocation(pos) }
        removeTestProvider()
        releaseWakeLock()
        _state.value = MockLocationState.IDLE
        locationRepository.setMockMode(MockMode.TELEPORT)
        locationRepository.stopSpoofing()
        locationRepository.setActiveRouteId(null)
        stopSelf()
        Log.i(TAG, "Spoofing stopped")
    }

    /**
     * Follower-only pause: leader stopped spoofing. Unlike [stopSpoofing], this never calls
     * [stopSelf] — the service, notification, and follower polling all stay alive so the leader's
     * next active tick can resume the mirror automatically (see [enterFollowerMode]).
     */
    private fun pauseFollowerForInactiveLeader() {
        releaseWakeLock()
        locationRepository.stopSpoofing()
        _state.value = MockLocationState.IDLE
        Log.i(TAG, "Leader stopped spoofing — pausing follower mirror")
    }

    fun getCurrentPosition(): LatLng = positionRef.get()

    private fun enterFollowerMode(
        host: String,
        port: Int,
        groupId: String,
    ) {
        stopFollowerRestoration()
        serviceScope.launch {
            // Await both cancellations before activating follower mode to avoid a race where
            // a completing replay/roam tick sets the mode back after we set FOLLOWER.
            coroutineScope {
                launch { handleReplayCancel() }
                launch { roamingRepository.stopRoaming() }
            }
            locationRepository.setMockMode(MockMode.FOLLOWER)
            followerCatchUp.clear()
            followerSyncClient.startPolling(
                host,
                port,
                groupId,
                onGroupLost = {
                    Log.w(TAG, "Group $groupId lost — attempting NSD re-discovery before giving up")
                    serviceScope.launch {
                        var resolved: Pair<String, Int>? = null
                        for (attempt in 0..AppConstants.SyncConstants.NSD_REDISCOVERY_RETRY_COUNT) {
                            resolved = groupNsdManager.discoverByCode(groupId)
                            if (resolved != null) break
                            Log.w(TAG, "NSD re-discovery attempt $attempt failed for group $groupId")
                        }
                        if (resolved != null) {
                            val (newHost, newPort) = resolved
                            Log.i(TAG, "Re-discovered group $groupId at $newHost:$newPort — reconnecting")
                            enterFollowerMode(newHost, newPort, groupId)
                        } else {
                            Log.w(TAG, "NSD re-discovery exhausted for group $groupId — leaving group")
                            exitFollowerMode()
                            groupRepository.leaveGroup()
                            groupRepository.emitGroupLost()
                        }
                    }
                },
            ) { update ->
                val (lat, lon, _, bearing, active) = update
                followerCatchUp.setTarget(LatLng(lat, lon), bearing)
                groupRepository.setLeaderPosition(LatLng(lat, lon))
                when (followerCatchUp.handleLeaderActiveUpdate(active, _state.value)) {
                    FollowerBootstrapAction.BOOTSTRAP -> {
                        serviceScope.launch {
                            startSpoofing(lat, lon)
                            // startSpoofing() unconditionally sets mode to TELEPORT — reassert
                            // FOLLOWER so advanceFollowerCatchUp() doesn't no-op on later ticks.
                            locationRepository.setMockMode(MockMode.FOLLOWER)
                        }
                    }

                    FollowerBootstrapAction.PAUSE -> {
                        serviceScope.launch { pauseFollowerForInactiveLeader() }
                    }

                    FollowerBootstrapAction.NONE -> {
                        Unit
                    }
                }
            }
            Log.i(TAG, "Entered FOLLOWER mode for group $groupId at $host:$port")
        }
    }

    internal fun exitFollowerMode() {
        stopFollowerRestoration()
        followerSyncClient.stopPolling()
        followerCatchUp.clear()
        groupRepository.setLeaderPosition(null)
        locationRepository.setMockMode(MockMode.TELEPORT)
        Log.i(TAG, "Exited FOLLOWER mode")
    }

    /** Manual override: snap straight to the last-known leader position instead of walking there. */
    internal fun teleportToLeaderNow() {
        val target =
            followerCatchUp.currentTarget() ?: run {
                Log.w(TAG, "Teleport to leader requested but leader position not yet known")
                groupRepository.emitTeleportUnavailable()
                return
            }
        writeCurrentPosition(target.latitude, target.longitude, syncRepository = true)
        followerCatchUp.markArrived()
        // Shares the same cooldown clock as every other teleport (Favorites, map long-press) —
        // see CooldownEngine/settingsRepository.getLastTeleportTime().
        serviceScope.launch { settingsRepository.setLastTeleportTime(System.currentTimeMillis()) }
        Log.i(TAG, "Follower teleported to leader at (${target.latitude}, ${target.longitude})")
    }

    private fun enterLeaderMode(groupId: String) {
        stopFollowerRestoration()
        serviceScope.launch {
            try {
                val host =
                    NetworkUtils.getLocalIpAddress() ?: run {
                        Log.e(TAG, "Cannot determine local IP — ensure Wi-Fi is connected")
                        return@launch
                    }
                val port =
                    if (!leaderSyncServer.isRunning) {
                        val newPort = leaderSyncServer.start(groupId)
                        groupNsdManager.startAdvertising(code = groupId, port = newPort)
                        newPort
                    } else {
                        leaderSyncServer.currentPort
                    }
                // Always call createGroup to ensure the VM picks up the current host:port
                // (covers both fresh start and idempotent re-entry when already running).
                groupRepository.createGroup(host = host, port = port, groupId = groupId)
                Log.i(TAG, "Entered LEADER mode: $groupId at $host:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start leader mode", e)
            }
        }
    }

    private fun exitLeaderMode() {
        leaderSyncServer.stop()
        groupNsdManager.stopAdvertising()
        leaderSharingEnabled = false
        Log.i(TAG, "Exited LEADER mode")
    }

    /**
     * Starts an async follower restoration job with exponential backoff + jitter.
     * On device boot (START_STICKY), the leader may not have advertised yet, so this job
     * retries NSD discovery over a ~60-second window rather than giving up immediately.
     * Automatically stops on success, exhaustion, or [stopFollowerRestoration].
     */
    private fun startFollowerRestoration(groupId: String) {
        stopFollowerRestoration()
        followerRestorationJob =
            serviceScope.launch {
                val maxAttempts = AppConstants.FollowerRestorationConstants.MAX_RETRY_ATTEMPTS
                var delayMs = AppConstants.FollowerRestorationConstants.INITIAL_RETRY_DELAY_MS
                val maxDelayMs = AppConstants.FollowerRestorationConstants.MAX_RETRY_DELAY_MS
                val jitterRangeMs = AppConstants.FollowerRestorationConstants.RETRY_JITTER_MS

                for (attempt in 0 until maxAttempts) {
                    if (!isActive) return@launch
                    try {
                        val resolved = groupNsdManager.discoverByCode(groupId)
                        if (resolved != null) {
                            val (host, port) = resolved
                            Log.i(TAG, "Follower restoration succeeded on attempt $attempt for group $groupId at $host:$port")
                            enterFollowerMode(host, port, groupId)
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Follower restoration attempt $attempt failed for group $groupId", e)
                    }

                    if (attempt < maxAttempts - 1) {
                        val jitterMs = Random.nextLong(-jitterRangeMs, jitterRangeMs)
                        delay(delayMs + jitterMs)
                        delayMs = minOf(delayMs * 2, maxDelayMs)
                    }
                }
                Log.w(TAG, "Follower restoration exhausted after $maxAttempts attempts for group $groupId — staying idle")
            }
    }

    /** Cancels any in-flight follower restoration job. */
    private fun stopFollowerRestoration() {
        followerRestorationJob?.cancel()
        followerRestorationJob = null
    }

    private fun observeGroupState() {
        serviceScope.launch {
            groupRepository.groupState.collect { state ->
                leaderSharingEnabled = state.role == GroupRole.LEADER && state.sharingEnabled
            }
        }
    }

    private fun handleReplayStart(
        routeId: String,
        isBackward: Boolean,
        speedMs: Double,
        isLoopingOverride: Boolean? = null,
        returnPosition: LatLng? = null,
        followRoadsToStart: Boolean = false,
    ) = replayOrchestrator.handleStart(routeId, isBackward, speedMs, isLoopingOverride, returnPosition, followRoadsToStart)

    private fun handleEphemeralReplayStart(
        waypoints: List<LatLng>,
        speedMs: Double,
    ) = replayOrchestrator.handleEphemeralStart(waypoints, speedMs)

    private fun handleReplayPause() = replayOrchestrator.handlePause()

    private fun handleReplayResume(speedMs: Double) = replayOrchestrator.handleResume(speedMs)

    private suspend fun handleReplayStop() = replayOrchestrator.handleStop()

    private suspend fun handleReplayCancel() = replayOrchestrator.handleCancel()

    private fun handleReplayJumpNext() = replayOrchestrator.handleJumpToNextWaypoint()

    private fun handleReplayJumpPrevious() = replayOrchestrator.handleJumpToPreviousWaypoint()

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
            } else {
                addLegacyTestProvider()
            }
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

    // Pre-31 devices lack ProviderProperties.Builder + the ProviderProperties addTestProvider
    // overload — same property values as the API 31+ Builder path above.
    @Suppress("DEPRECATION")
    private fun addLegacyTestProvider() {
        locationManager.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false,
            false,
            false,
            false,
            true,
            true,
            true,
            ProviderProperties.POWER_USAGE_HIGH,
            ProviderProperties.ACCURACY_FINE,
        )
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
        val current = suspendedPhase.get()
        val next = advanceSuspendedPhase(current, now, realism.suspendedMockingEnabled, mode, Random.Default)
        if (next != current) {
            suspendedPhase.set(next)
            Log.i(TAG, "Realism: suspended phase=${if (next.isActive) "PAUSED" else "PUSHING"}")
        }
    }

    /**
     * Starts an async real-elevation fetch if due: not already in flight, the feature is enabled,
     * the fetch interval has elapsed, and no manual override is active (checked again inside the
     * launched coroutine, since the override may have been set since this call started). Shared by
     * [startSpoofing] and every tick of [captureSnapshot] so "fetch now if due" is one code path.
     */
    private fun maybeFetchElevation(
        nowMs: Long,
        lat: Double,
        lon: Double,
    ) {
        if (elevationFetchInFlight) return
        if (!realism.realElevationEnabled) return
        if (nowMs - lastElevationFetchMs < AppConstants.RealismConstants.ELEVATION_FETCH_INTERVAL_MS) return
        lastElevationFetchMs = nowMs
        elevationFetchInFlight = true
        serviceScope.launch {
            if (settingsRepository.getBaseAltitudeOverride().first() == null) {
                elevationRepository.fetchElevationMeters(lat, lon)?.let { targetBaseAltitudeMeters = it }
            }
            elevationFetchInFlight = false
        }
    }

    /**
     * Freezes all @Volatile fields into an immutable [LocationSnapshot] to eliminate TOCTOU races
     * in the tick loop. Also computes [LocationSnapshot.shouldApplyMovingJitter] and refreshes the
     * slow-churn satellite counts when their interval has elapsed.
     */
    internal fun captureSnapshot(nowMs: Long): LocationSnapshot {
        val mode = locationRepository.currentMode.value
        val jitterIntervalMs = realism.jitterIntervalSeconds * 1000L
        val shouldApplyMovingJitter = (nowMs - lastJitterTimestampMs) >= jitterIntervalMs
        val idleJitterIntervalMs = realism.jitterIdleIntervalSeconds * 1000L
        val shouldApplyIdleJitter = (nowMs - lastIdleJitterTimestampMs) >= idleJitterIntervalMs
        val suspendedPhaseSnapshot = suspendedPhase.get()

        // Slow satellite churn
        if (realism.satelliteExtrasEnabled &&
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

        val currentPos = positionRef.get()
        currentBaseAltitudeMeters =
            stepToward(
                currentBaseAltitudeMeters,
                targetBaseAltitudeMeters,
                AppConstants.RealismConstants.ALTITUDE_TARGET_STEP_METERS_PER_TICK,
            )
        maybeFetchElevation(nowMs, currentPos.latitude, currentPos.longitude)
        val speedMs = if (mode == MockMode.FOLLOWER) followerCatchUp.currentSpeedMs() else currentSpeedMs
        val bearing = if (mode == MockMode.FOLLOWER) followerCatchUp.currentBearing() else currentBearing
        return LocationSnapshot(
            latitude = currentPos.latitude,
            longitude = currentPos.longitude,
            speedMs = speedMs,
            bearing = bearing,
            lastNonZeroBearing = lastNonZeroBearing,
            mode = mode,
            jitterIdleRadiusMeters = realism.jitterIdleRadiusMeters,
            jitterMovingRadiusMeters = realism.jitterMovingRadiusMeters,
            shouldApplyMovingJitter = shouldApplyMovingJitter,
            shouldApplyIdleJitter = shouldApplyIdleJitter,
            altitudeMeters = currentAltitudeMeters,
            humanAltitudeOffsetMeters = humanAltitudeOffsetMeters,
            warmupStartMs = warmupStartMs,
            warmupEnabled = realism.warmupEnabled,
            bearingHoldEnabled = realism.bearingHoldEnabled,
            altitudeEnabled = realism.altitudeEnabled,
            satelliteExtrasEnabled = realism.satelliteExtrasEnabled,
            speedIdleVariationPct = realism.speedIdleVariationPct,
            speedMovingVariationPct = realism.speedMovingVariationPct,
            activeProfileSpeedMs = realism.activeProfileSpeedMs,
            suspendedPhaseStartMs = suspendedPhaseSnapshot.startMs,
            isSuspendedPhase = suspendedPhaseSnapshot.isActive,
            cachedSatelliteCount = cachedSatelliteCount,
            cachedUsedInFixCount = cachedUsedInFixCount,
            baseAltitudeMeters = currentBaseAltitudeMeters,
            altitudeJitterRadiusMeters = realism.altitudeJitterRadiusMeters,
        )
    }

    /**
     * Android adapter — the only place that touches [android.location.Location] APIs. Translates
     * the pure [LocationFix] into a test provider location update.
     */
    private fun applyToProvider(
        fix: LocationFix,
        nowNanos: Long,
    ) {
        val loc =
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = fix.latitude
                longitude = fix.longitude
                altitude = fix.altitudeMeters
                accuracy = fix.accuracyMeters
                speed = fix.speedMs
                bearing = fix.bearing
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = nowNanos
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

    /**
     * Applies one [FollowerCatchUpCoordinator.advance] step. Called once per tick, before
     * [captureSnapshot] reads [positionRef]. FOLLOWER mode has no other in-process writer of its
     * own, so this syncs [LocationRepository] (consumed by the map UI) via [writeCurrentPosition]
     * itself rather than the caller pairing the two writes manually. No-ops outside FOLLOWER mode.
     */
    internal fun advanceFollowerCatchUp() {
        if (locationRepository.currentMode.value != MockMode.FOLLOWER) return
        val result = followerCatchUp.advance(positionRef.get(), realism.activeProfileSpeedMs) ?: return
        writeCurrentPosition(result.latitude, result.longitude, syncRepository = true)
    }

    private fun pushLocationUpdate() {
        if (!providerAdded) return
        try {
            advanceFollowerCatchUp()
            updateSuspendedPhase()
            val nowNanos = SystemClock.elapsedRealtimeNanos()
            val nowMs = nowNanos / 1_000_000L
            val snapshot = captureSnapshot(nowMs)
            val fix = buildLocation(snapshot, nowMs, Random.Default)
            currentAltitudeMeters = fix.altitudeMeters - fix.humanAltitudeOffsetMeters
            humanAltitudeOffsetMeters = fix.humanAltitudeOffsetMeters
            locationRepository.setReportedAltitude(fix.altitudeMeters)
            val tickIntervalMs = if (lastTickMs == 0L) 0L else nowMs - lastTickMs
            lastTickMs = nowMs
            locationRepository.setDebugStats(
                DebugStats(
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    speedMs = fix.speedMs,
                    altitudeMeters = fix.altitudeMeters,
                    accuracyMeters = fix.accuracyMeters,
                    bearing = fix.bearing,
                    tickIntervalMs = tickIntervalMs,
                ),
            )
            if (snapshot.speedMs > 0f) lastNonZeroBearing = snapshot.bearing
            if (snapshot.shouldApplyMovingJitter && snapshot.mode != MockMode.TELEPORT) {
                lastJitterTimestampMs = nowMs
            }
            if (snapshot.shouldApplyIdleJitter &&
                (snapshot.mode == MockMode.TELEPORT || (snapshot.mode == MockMode.FOLLOWER && snapshot.speedMs == 0f))
            ) {
                lastIdleJitterTimestampMs = nowMs
            }
            applyToProvider(fix, nowNanos)
            if (shouldPersistLastLocation(lastPersistedLocationMs, nowMs)) {
                lastPersistedLocationMs = nowMs
                val pos = positionRef.get()
                serviceScope.launch { settingsRepository.setLastLocation(pos) }
            }
            if (leaderSharingEnabled) {
                leaderSyncServer.push(
                    SyncPositionUpdate(
                        timestamp = System.currentTimeMillis(),
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        speedMs = fix.speedMs,
                        bearing = fix.bearing,
                        seq = 0,
                        active = _state.value != MockLocationState.IDLE && _state.value != MockLocationState.ERROR,
                    ),
                )
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to push location update", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error pushing location update", e)
        }
    }

    private fun createNotificationChannel() = createMockLocationNotificationChannels(this)

    private fun postPermissionErrorNotification() = postMockLocationPermissionErrorNotification(this)

    private fun buildNotification(
        replayActive: Boolean = false,
        replayPaused: Boolean = false,
        hideNotification: Boolean,
    ): Notification = buildMockLocationNotification(this, replayActive, replayPaused, hideNotification)
}
