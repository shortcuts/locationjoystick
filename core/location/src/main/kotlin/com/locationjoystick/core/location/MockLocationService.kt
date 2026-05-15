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
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.BuildConfig
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.routing.RouteReplayEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MockLocationService : Service() {
    companion object {
        private const val TAG = "MockLocationService"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_PERM_ERROR = 1002
        private const val CHANNEL_ID = "location_spoof_channel"
        private const val CHANNEL_ID_PERM_ERROR = "location_perm_error_channel"
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val LOCATION_ACCURACY = 3.0f

        private const val JOYSTICK_SERVICE_CLASS =
            "com.locationjoystick.feature.joystick.impl.JoystickOverlayService"
        private const val WIDGET_SERVICE_CLASS =
            "com.locationjoystick.feature.widget.impl.FloatingWidgetService"

        const val ACTION_START = "com.locationjoystick.core.location.ACTION_START"
        const val ACTION_STOP = "com.locationjoystick.core.location.ACTION_STOP"
        const val ACTION_UPDATE_POSITION = "com.locationjoystick.core.location.ACTION_UPDATE_POSITION"
        const val ACTION_ROUTE_REPLAY_START = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_START"
        const val ACTION_ROUTE_REPLAY_PAUSE = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_PAUSE"
        const val ACTION_ROUTE_REPLAY_RESUME = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_RESUME"
        const val ACTION_ROUTE_REPLAY_STOP = "com.locationjoystick.core.location.ACTION_ROUTE_REPLAY_STOP"

        const val EXTRA_ROUTE_ID = "extra_route_id"
        const val EXTRA_IS_BACKWARD = "extra_is_backward"
        const val EXTRA_SPEED_MS = "extra_speed_ms"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MockLocationService = this@MockLocationService
    }

    private val binder = LocalBinder()

    @Inject lateinit var locationManager: LocationManager

    @Inject lateinit var locationRepository: LocationRepository

    @Inject lateinit var routeRepository: RouteRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var routeReplayEngine: RouteReplayEngine

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "MockLocationService coroutine crashed", throwable)
        _state.value = MockLocationState.ERROR
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private var updateJob: Job? = null

    private val _state = MutableStateFlow(MockLocationState.IDLE)
    val state: StateFlow<MockLocationState> = _state.asStateFlow()

    @Volatile private var currentLat: Double = 0.0

    @Volatile private var currentLon: Double = 0.0

    @Volatile private var currentSpeedMs: Float = 0.0f

    @Volatile private var currentBearing: Float = 0.0f

    @Volatile private var jitterIdleRadiusMeters: Double = 0.0

    @Volatile private var jitterMovingRadiusMeters: Double = 10.0

    private var providerAdded = false

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
                        if (updateJob == null) {
                            setupTestProvider()
                            startUpdateLoop()
                            Log.i(TAG, "State changed to RUNNING - started update loop")
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
                        if (updateJob != null) {
                            updateJob?.cancel()
                            updateJob = null
                            removeTestProvider()
                            Log.i(TAG, "State changed to IDLE/ERROR - stopped update loop")
                        }
                        stopService(Intent().setClassName(packageName, JOYSTICK_SERVICE_CLASS))
                        stopService(Intent().setClassName(packageName, WIDGET_SERVICE_CLASS))
                        Log.i(TAG, "Overlay services stopped")
                    }

                    MockLocationState.PAUSED -> {
                        if (updateJob != null) {
                            updateJob?.cancel()
                            updateJob = null
                            Log.i(TAG, "State changed to PAUSED - paused update loop")
                        }
                        // Overlays remain visible during pause
                    }
                }
            }
        }
        serviceScope.launch {
            locationRepository.currentPosition
                .catch { e -> Log.e(TAG, "currentPosition flow error", e) }
                .collect { position ->
                    if (position != null) {
                        currentLat = position.latitude
                        currentLon = position.longitude
                    }
                }
        }
        serviceScope.launch {
            settingsRepository.getJitterIdleRadius()
                .catch { e -> Log.e(TAG, "jitterIdleRadius flow error", e) }
                .collect { jitterIdleRadiusMeters = it }
        }
        serviceScope.launch {
            settingsRepository.getJitterMovingRadius()
                .catch { e -> Log.e(TAG, "jitterMovingRadius flow error", e) }
                .collect { jitterMovingRadiusMeters = it }
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
                val lat = intent.getDoubleExtra("lat", 48.8566)
                val lon = intent.getDoubleExtra("lon", 2.3522)
                startSpoofing(lat, lon)
            }

            ACTION_STOP -> {
                stopSpoofing()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE_POSITION -> {
                val lat = intent?.getDoubleExtra("lat", currentLat) ?: currentLat
                val lon = intent?.getDoubleExtra("lon", currentLon) ?: currentLon
                updatePosition(lat, lon)
            }

            ACTION_ROUTE_REPLAY_START -> {
                val routeId = intent.getStringExtra(EXTRA_ROUTE_ID) ?: return START_STICKY
                val isBackward = intent.getBooleanExtra(EXTRA_IS_BACKWARD, false)
                val speedMs = intent.getDoubleExtra(EXTRA_SPEED_MS, 1.4)
                handleReplayStart(routeId, isBackward, speedMs)
            }

            ACTION_ROUTE_REPLAY_PAUSE -> {
                handleReplayPause()
            }

            ACTION_ROUTE_REPLAY_RESUME -> {
                val speedMs = intent.getDoubleExtra(EXTRA_SPEED_MS, 1.4)
                handleReplayResume(speedMs)
            }

            ACTION_ROUTE_REPLAY_STOP -> {
                serviceScope.launch { handleReplayStop() }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopSpoofing()
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
        locationRepository.setPositionInternal(LatLng(lat, lon))

        setupTestProvider()
        startUpdateLoop()
        _state.value = MockLocationState.RUNNING
        serviceScope.launch { locationRepository.startSpoofing() }
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
        serviceScope.launch {
            locationRepository.stopSpoofing()
            locationRepository.setActiveRouteId(null)
        }
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
            val route = routeRepository.getRouteWithWaypoints(routeId).first() ?: return@launch
            if (route.waypoints.size < 2) return@launch

            val ordered = if (isBackward) route.waypoints.reversed() else route.waypoints
            val latLngs = ordered.map { it.position }
            val startPos = latLngs.first()

            currentLat = startPos.latitude
            currentLon = startPos.longitude
            currentSpeedMs = 0.0f
            currentBearing = 0.0f

            setupTestProvider()
            _state.value = MockLocationState.RUNNING
            locationRepository.startSpoofing()
            locationRepository.setActiveRouteId(routeId)
            locationRepository.setIsReplayBackward(isBackward)

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
                onComplete = { stopSpoofing() },
            )
        }
    }

    private fun handleReplayPause() {
        routeReplayEngine.pause()
        updateJob?.cancel()
        updateJob = null
        _state.value = MockLocationState.PAUSED
        serviceScope.launch { locationRepository.pauseSpoofing() }
        Log.i(TAG, "Replay paused")
    }

    private fun handleReplayResume(speedMs: Double) {
        _state.value = MockLocationState.RUNNING
        serviceScope.launch { locationRepository.startSpoofing() }
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
            onComplete = { stopSpoofing() },
        )
        Log.i(TAG, "Replay resumed at ${speedMs}m/s")
    }

    private suspend fun handleReplayStop() {
        routeReplayEngine.stop()
        stopSpoofing()
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
                    .setHasAltitudeSupport(false)
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
                    delay(UPDATE_INTERVAL_MS)
                }
            }
    }

    private fun pushLocationUpdate() {
        if (!providerAdded) return
        try {
            val jitterRadius = if (currentSpeedMs > 0f) jitterMovingRadiusMeters else jitterIdleRadiusMeters
            val (outLat, outLon, outAccuracy) =
                if (jitterRadius > 0.0) {
                    applyJitter(currentLat, currentLon, jitterRadius)
                } else {
                    Triple(currentLat, currentLon, LOCATION_ACCURACY)
                }
            val location =
                Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = outLat
                    longitude = outLon
                    altitude = 0.0
                    accuracy = outAccuracy
                    speed = currentSpeedMs
                    bearing = currentBearing
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to push location update", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error pushing location update", e)
        }
    }

    private fun applyJitter(
        lat: Double,
        lon: Double,
        sigmaMeters: Double,
    ): Triple<Double, Double, Float> {
        val u1 = Math.random().coerceAtLeast(Double.MIN_VALUE)
        val u2 = Math.random()
        val mag = sigmaMeters * kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1))
        val angle = 2.0 * Math.PI * u2
        val dlat = mag * kotlin.math.cos(angle) / 111_111.0
        val dlon = mag * kotlin.math.sin(angle) / (111_111.0 * kotlin.math.cos(Math.toRadians(lat)))
        val accuracy = (LOCATION_ACCURACY + (Math.random() * 3.0 - 1.5).toFloat()).coerceIn(2.0f, 5.0f)
        return Triple(lat + dlat, lon + dlon, accuracy)
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Location Spoofing",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Active while mock location is running"
                setShowBadge(false)
            }
        val errorChannel =
            NotificationChannel(
                CHANNEL_ID_PERM_ERROR,
                "Permission Errors",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Shown when required permissions are missing"
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
                .setContentTitle("Permissions missing")
                .setContentText("Open the app and complete setup to start spoofing.")
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
            .setContentTitle("Mock location active")
            .setContentText("locationjoystick is spoofing your GPS position")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopPendingIntent,
            ).setOngoing(true)
            .setSilent(true)
            .build()
    }
}
