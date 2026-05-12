package com.locationjoystick.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
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
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.routing.RouteReplayEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MockLocationService : Service() {

    companion object {
        private const val TAG = "MockLocationService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "location_spoof_channel"
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
    @Inject lateinit var routeReplayEngine: RouteReplayEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var updateJob: Job? = null

    private val _state = MutableStateFlow(MockLocationState.IDLE)
    val state: StateFlow<MockLocationState> = _state.asStateFlow()

    @Volatile private var currentLat: Double = 0.0
    @Volatile private var currentLon: Double = 0.0
    @Volatile private var currentSpeedMs: Float = 0.0f
    @Volatile private var currentBearing: Float = 0.0f

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
            locationRepository.currentPosition.collect { position ->
                if (position != null) {
                    currentLat = position.latitude
                    currentLon = position.longitude
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            ACTION_ROUTE_REPLAY_PAUSE -> handleReplayPause()
            ACTION_ROUTE_REPLAY_RESUME -> {
                val speedMs = intent.getDoubleExtra(EXTRA_SPEED_MS, 1.4)
                handleReplayResume(speedMs)
            }
            ACTION_ROUTE_REPLAY_STOP -> serviceScope.launch { handleReplayStop() }
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopSpoofing()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun startSpoofing(lat: Double, lon: Double) {
        if (_state.value == MockLocationState.RUNNING) {
            Log.i(TAG, "Spoofing already running; ignoring duplicate startSpoofing()")
            return
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

    fun updatePosition(lat: Double, lon: Double) {
        currentLat = lat
        currentLon = lon
        locationRepository.setPositionInternal(LatLng(lat, lon))
    }

    // Joystick/walk callers: update locationRepository before calling this method (sets speed/bearing only).
    // Route replay uses onPositionUpdate lambda instead — does not call this method.
    fun updatePositionWithVector(lat: Double, lon: Double, speedMs: Float, bearing: Float) {
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

    private fun handleReplayStart(routeId: String, isBackward: Boolean, speedMs: Double) {
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
            startUpdateLoop()
            _state.value = MockLocationState.RUNNING
            locationRepository.startSpoofing()
            locationRepository.setActiveRouteId(routeId)
            locationRepository.setIsReplayBackward(isBackward)

            routeReplayEngine.start(
                waypoints = latLngs,
                speedMs = speedMs,
                onPositionUpdate = { pos ->
                    currentLat = pos.latitude
                    currentLon = pos.longitude
                    locationRepository.setPositionInternal(pos)
                },
                onComplete = {
                    _state.value = MockLocationState.IDLE
                    serviceScope.launch {
                        locationRepository.stopSpoofing()
                        locationRepository.setActiveRouteId(null)
                    }
                    updateJob?.cancel()
                    updateJob = null
                },
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
        startUpdateLoop()
        _state.value = MockLocationState.RUNNING
        serviceScope.launch { locationRepository.startSpoofing() }
        routeReplayEngine.resume(
            onPositionUpdate = { pos ->
                currentLat = pos.latitude
                currentLon = pos.longitude
                locationRepository.setPositionInternal(pos)
            },
            onComplete = {
                _state.value = MockLocationState.IDLE
                serviceScope.launch {
                    locationRepository.stopSpoofing()
                    locationRepository.setActiveRouteId(null)
                }
                updateJob?.cancel()
                updateJob = null
            },
        )
        Log.i(TAG, "Replay resumed at ${speedMs}m/s")
    }

    private suspend fun handleReplayStop() {
        routeReplayEngine.stop()
        stopSpoofing()
    }

    private fun setupTestProvider() {
        if (providerAdded) return
        try {
            val properties = ProviderProperties.Builder()
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
        updateJob = serviceScope.launch {
            while (isActive) {
                pushLocationUpdate()
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun pushLocationUpdate() {
        if (!providerAdded) return
        try {
            val location = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = currentLat
                longitude = currentLon
                altitude = 0.0
                accuracy = LOCATION_ACCURACY
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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Spoofing",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Active while mock location is running"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.let { intent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mock location active")
            .setContentText("locationjoystick is spoofing your GPS position")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopPendingIntent,
            )
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
