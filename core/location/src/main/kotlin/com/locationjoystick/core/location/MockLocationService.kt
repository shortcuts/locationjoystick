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
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.constants.AppConstants.ServiceConstants
import com.locationjoystick.core.common.util.advancePosition
import com.locationjoystick.core.common.util.calculateBearing
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.BuildConfig
import com.locationjoystick.core.model.ElevationMode
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.SyncPositionUpdate
import com.locationjoystick.core.routing.RouteReplayEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Inet4Address
import java.net.NetworkInterface
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
        const val ACTION_ROUTE_APPEND_WAYPOINT = AppConstants.ServiceConstants.ACTION_ROUTE_APPEND_WAYPOINT
        const val ACTION_ENTER_FOLLOWER = AppConstants.ServiceConstants.ACTION_ENTER_FOLLOWER
        const val ACTION_EXIT_FOLLOWER = AppConstants.ServiceConstants.ACTION_EXIT_FOLLOWER

        const val EXTRA_ROUTE_ID = AppConstants.ServiceConstants.EXTRA_ROUTE_ID
        const val EXTRA_IS_BACKWARD = AppConstants.ServiceConstants.EXTRA_IS_BACKWARD
        const val EXTRA_SPEED_MS = AppConstants.ServiceConstants.EXTRA_SPEED_MS
        const val EXTRA_WAYPOINT_LAT = AppConstants.ServiceConstants.EXTRA_WAYPOINT_LAT
        const val EXTRA_WAYPOINT_LON = AppConstants.ServiceConstants.EXTRA_WAYPOINT_LON
        const val EXTRA_IS_EPHEMERAL = AppConstants.ServiceConstants.EXTRA_IS_EPHEMERAL
        const val EXTRA_IS_LOOPING = AppConstants.ServiceConstants.EXTRA_IS_LOOPING
        const val EXTRA_RETURN_LAT = AppConstants.ServiceConstants.EXTRA_RETURN_LAT
        const val EXTRA_RETURN_LON = AppConstants.ServiceConstants.EXTRA_RETURN_LON
    }

    inner class LocalBinder : Binder() {
        fun getService(): MockLocationService = this@MockLocationService
    }

    fun setElevationMode(mode: ElevationMode?) {
        currentElevationMode = mode
    }

    private val binder = LocalBinder()

    @Inject lateinit var locationManager: LocationManager

    @Inject lateinit var locationRepository: LocationRepository

    @Inject lateinit var roamingRepository: RoamingRepository

    @Inject lateinit var routeRepository: RouteRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var routeReplayEngine: RouteReplayEngine

    @Inject lateinit var sensorInjector: SensorInjector

    @Inject lateinit var leaderSyncServer: LeaderSyncServer

    @Inject lateinit var followerSyncClient: FollowerSyncClient

    @Inject lateinit var groupRepository: GroupRepository

    @Inject lateinit var groupNsdManager: GroupNsdManager

    private val notificationManager: android.app.NotificationManager by lazy {
        getSystemService(android.app.NotificationManager::class.java)
    }

    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "MockLocationService coroutine crashed", throwable)
            _state.value = MockLocationState.ERROR
        }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    private lateinit var replayOrchestrator: ReplayOrchestrator

    /** Guards concurrent access to updateJob to prevent double-start from rapid state emissions. */
    private val updateJobMutex = Mutex()

    @Volatile private var updateJob: Job? = null

    private val _state = MutableStateFlow(MockLocationState.IDLE)
    val state: StateFlow<MockLocationState> = _state.asStateFlow()

    private val positionRef = AtomicReference(LatLng(0.0, 0.0))

    @Volatile private var currentSpeedMs: Float = 0.0f

    @Volatile private var currentBearing: Float = 0.0f

    @Volatile private var lastJitterTimestampMs: Long = 0L

    @Volatile private var lastIdleJitterTimestampMs: Long = 0L

    @Volatile private var providerAdded = false

    /** Realism settings observed from [SettingsRepository] and read each tick in [captureSnapshot]. */
    private val realism = RealismSettingsState()

    @Volatile private var humanAltitudeOffsetMeters: Double = AppConstants.RealismConstants.ALTITUDE_HUMAN_OFFSET_METERS

    @Volatile private var currentElevationMode: ElevationMode? = null

    @Volatile private var leaderSharingEnabled: Boolean = false

    // Per-tick realism state

    /** Bearing from the last tick where speedMs > 0; held when the device is stationary. */
    @Volatile private var lastNonZeroBearing: Float = 0f

    /** Seed altitude for the next tick's Gaussian walk; written back from LocationFix.altitudeMeters after each successful tick. */
    @Volatile private var currentAltitudeMeters: Double =
        AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS

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

    override fun onCreate() {
        super.onCreate()
        replayOrchestrator =
            ReplayOrchestrator(
                locationRepository = locationRepository,
                routeRepository = routeRepository,
                roamingRepository = roamingRepository,
                routeReplayEngine = routeReplayEngine,
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
            locationRepository.currentPosition.collect { position ->
                if (position != null) {
                    writeCurrentPosition(position.latitude, position.longitude)
                }
            }
        }
        // Reactive notification refresh — source of truth is repository flows, not _state.
        // distinctUntilChanged prevents notify storm during 1 Hz position ticks.
        serviceScope.launch {
            combine(locationRepository.currentMode, locationRepository.mockLocationState) { mode, state ->
                Pair(mode, state)
            }.distinctUntilChanged()
                .collect { (mode, state) ->
                    // Double-guarded: walk-to PAUSED never triggers replayPaused=true
                    val replayActive = mode == MockMode.ROUTE_REPLAY
                    val replayPaused = mode == MockMode.ROUTE_REPLAY && state == MockLocationState.PAUSED
                    notificationManager.notify(
                        AppConstants.NotificationConstants.ID_ACTIVE,
                        buildMockLocationNotification(this@MockLocationService, replayActive, replayPaused),
                    )
                }
        }
        // Jitter and realism settings — each updates a @Volatile field consumed by captureSnapshot().
        realism.observe(serviceScope, settingsRepository)
    }

    /**
     * Single write entry point for position. All position writers funnel through here so the
     * latitude/longitude pair is always written atomically, eliminating torn reads between a
     * mid-tick teleport and [updatePositionWithVector].
     */
    private fun writeCurrentPosition(
        lat: Double,
        lon: Double,
    ) {
        positionRef.set(LatLng(lat, lon))
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
                        // Leader may have restarted with a new OS-assigned port — re-resolve via NSD
                        // rather than trusting the stale host:port persisted in DataStore.
                        val resolved = groupNsdManager.discoverByCode(id)
                        if (resolved == null) {
                            Log.w(TAG, "OS restart: NSD re-discovery failed for group $id — staying idle")
                            return@launch
                        }
                        val (host, port) = resolved
                        Log.i(TAG, "OS restart: resuming follower mode for group $id at $host:$port")
                        enterFollowerMode(host, port, id)
                        return@launch
                    }
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
                val speedMs = intent.getDoubleExtra(EXTRA_SPEED_MS, AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS)
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
                    handleReplayStart(routeId, isBackward, speedMs, isLoopingOverride, returnPosition)
                }
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
        // Don't call stopSpoofing() here — it calls stopSelf() which re-triggers onDestroy.
        // serviceScope.cancel() below cancels updateJob (child of the scope) automatically.
        updateJob = null
        removeTestProvider()
        // Persist last location on external kill (process killed, OOM, etc.)
        // stopSpoofing() handles this on normal stop, but onDestroy may be called without it.
        // Always persist location; the rememberLastLocation setting only controls whether to restore it.
        if (_state.value == MockLocationState.RUNNING) {
            val pos = positionRef.get()
            CoroutineScope(NonCancellable + Dispatchers.IO).launch { settingsRepository.setLastLocation(pos) }
        }
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

        writeCurrentPosition(lat, lon)
        currentSpeedMs = 0.0f
        currentBearing = 0.0f
        val now = SystemClock.elapsedRealtime()
        currentAltitudeMeters = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS
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
        sensorInjector.resetPedometerState()
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
        if (locationRepository.currentMode.value == MockMode.FOLLOWER) return
        writeCurrentPosition(lat, lon)
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
        if (locationRepository.currentMode.value == MockMode.FOLLOWER) return
        writeCurrentPosition(lat, lon)
        currentSpeedMs = speedMs
        currentBearing = bearing
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
        _state.value = MockLocationState.IDLE
        locationRepository.setMockMode(MockMode.TELEPORT)
        locationRepository.stopSpoofing()
        locationRepository.setActiveRouteId(null)
        stopSelf()
        Log.i(TAG, "Spoofing stopped")
    }

    fun getCurrentPosition(): LatLng = positionRef.get()

    private fun enterFollowerMode(
        host: String,
        port: Int,
        groupId: String,
    ) {
        serviceScope.launch {
            // Await both cancellations before activating follower mode to avoid a race where
            // a completing replay/roam tick sets the mode back after we set FOLLOWER.
            coroutineScope {
                launch { handleReplayCancel() }
                launch { roamingRepository.stopRoaming() }
            }
            locationRepository.setMockMode(MockMode.FOLLOWER)
            val spoofingStarted =
                java.util.concurrent.atomic
                    .AtomicBoolean(false)
            followerSyncClient.startPolling(
                host,
                port,
                groupId,
                onGroupLost = {
                    Log.w(TAG, "Group $groupId no longer exists — leaving group")
                    serviceScope.launch {
                        exitFollowerMode()
                        groupRepository.leaveGroup()
                    }
                },
            ) { lat, lon, speedMs, bearing ->
                if (spoofingStarted.compareAndSet(false, true) && _state.value != MockLocationState.RUNNING) {
                    serviceScope.launch { startSpoofing(lat, lon) }
                }
                writeCurrentPosition(lat, lon)
                currentSpeedMs = speedMs
                currentBearing = bearing
                locationRepository.setPositionInternal(LatLng(lat, lon))
            }
            Log.i(TAG, "Entered FOLLOWER mode for group $groupId at $host:$port")
        }
    }

    private fun exitFollowerMode() {
        followerSyncClient.stopPolling()
        locationRepository.setMockMode(MockMode.TELEPORT)
        Log.i(TAG, "Exited FOLLOWER mode")
    }

    private fun enterLeaderMode(groupId: String) {
        serviceScope.launch {
            try {
                val host =
                    getLocalIpAddress() ?: run {
                        Log.e(TAG, "Cannot determine local IP — ensure Wi-Fi is connected")
                        return@launch
                    }
                val port = leaderSyncServer.start(groupId)
                groupNsdManager.startLeader(code = groupId, port = port)
                groupRepository.createGroup(host = host, port = port, groupId = groupId)
                Log.i(TAG, "Entered LEADER mode: $groupId at $host:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start leader mode", e)
            }
        }
    }

    private fun exitLeaderMode() {
        leaderSyncServer.stop()
        groupNsdManager.stopLeader()
        leaderSharingEnabled = false
        Log.i(TAG, "Exited LEADER mode")
    }

    private fun observeGroupState() {
        serviceScope.launch {
            groupRepository.groupState.collect { state ->
                leaderSharingEnabled = state.role == GroupRole.LEADER && state.sharingEnabled
            }
        }
    }

    private fun getLocalIpAddress(): String? =
        try {
            NetworkInterface
                .getNetworkInterfaces()
                ?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { addr -> !addr.isLoopbackAddress && addr is Inet4Address }
                ?.hostAddress
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP", e)
            null
        }

    private fun handleReplayStart(
        routeId: String,
        isBackward: Boolean,
        speedMs: Double,
        isLoopingOverride: Boolean? = null,
        returnPosition: LatLng? = null,
    ) = replayOrchestrator.handleStart(routeId, isBackward, speedMs, isLoopingOverride, returnPosition)

    private fun handleEphemeralReplayStart(
        waypoints: List<LatLng>,
        speedMs: Double,
    ) = replayOrchestrator.handleEphemeralStart(waypoints, speedMs)

    private fun handleReplayPause() = replayOrchestrator.handlePause()

    private fun handleReplayResume(speedMs: Double) = replayOrchestrator.handleResume(speedMs)

    private suspend fun handleReplayStop() = replayOrchestrator.handleStop()

    private suspend fun handleReplayCancel() = replayOrchestrator.handleCancel()

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
        val current = suspendedPhase.get()
        val next = advanceSuspendedPhase(current, now, realism.suspendedMockingEnabled, mode, Random.Default)
        if (next != current) {
            suspendedPhase.set(next)
            Log.i(TAG, "Realism: suspended phase=${if (next.isActive) "PAUSED" else "PUSHING"}")
        }
    }

    /**
     * Freezes all @Volatile fields into an immutable [LocationSnapshot] to eliminate TOCTOU races
     * in the tick loop. Also computes [LocationSnapshot.shouldApplyMovingJitter] and refreshes the
     * slow-churn satellite counts when their interval has elapsed.
     */
    private fun captureSnapshot(nowMs: Long): LocationSnapshot {
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
        return LocationSnapshot(
            latitude = currentPos.latitude,
            longitude = currentPos.longitude,
            speedMs = currentSpeedMs,
            bearing = currentBearing,
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

    private fun pushLocationUpdate() {
        if (!providerAdded) return
        try {
            updateSuspendedPhase()
            val nowNanos = SystemClock.elapsedRealtimeNanos()
            val nowMs = nowNanos / 1_000_000L
            val snapshot = captureSnapshot(nowMs)
            val fix = buildLocation(snapshot, nowMs, Random.Default) ?: return
            currentAltitudeMeters = fix.altitudeMeters - fix.humanAltitudeOffsetMeters
            humanAltitudeOffsetMeters = fix.humanAltitudeOffsetMeters
            if (snapshot.speedMs > 0f) lastNonZeroBearing = snapshot.bearing
            if (snapshot.shouldApplyMovingJitter && snapshot.mode != MockMode.TELEPORT) {
                lastJitterTimestampMs = nowMs
            }
            if (snapshot.shouldApplyIdleJitter && snapshot.mode == MockMode.TELEPORT) {
                lastIdleJitterTimestampMs = nowMs
            }
            applyToProvider(fix, nowNanos)
            if (leaderSharingEnabled) {
                leaderSyncServer.push(
                    SyncPositionUpdate(
                        timestamp = System.currentTimeMillis(),
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        speedMs = fix.speedMs,
                        bearing = fix.bearing,
                        seq = 0,
                    ),
                )
            }
            val elevMode = currentElevationMode
            if (elevMode != null) {
                sensorInjector.inject(
                    elevMode,
                    AppConstants.ElevationConstants.DEFAULT_TILT_DEGREES,
                    realism.elevationTiltJitterDegrees,
                    realism.elevationNoiseAmplitudeMs2,
                    Random.Default,
                )
            }
            if (realism.pedometerMockingEnabled) sensorInjector.injectPedometerTick(snapshot.speedMs)
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
    ): Notification = buildMockLocationNotification(this, replayActive, replayPaused)
}
