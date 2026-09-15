package com.locationjoystick.core.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.locationjoystick.core.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Reads the phone's GPS for camera positioning without changing the cached mock location. */
@Singleton
class RealLocationRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val locationManager = context.getSystemService(LocationManager::class.java)

        @Suppress("MissingPermission")
        suspend fun getCurrentPosition(): Result<LatLng> =
            runCatching {
                check(
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED,
                ) { "Location permission is required" }

                val fresh =
                    withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            awaitCurrentGps()
                        } else {
                            awaitLegacyCurrentGps()
                        }
                    }
                val location = fresh ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                checkNotNull(location) { "No real GPS fix is available yet" }
                LatLng(location.latitude, location.longitude)
            }

        @Suppress("DEPRECATION", "MissingPermission")
        private suspend fun awaitLegacyCurrentGps(): Location? =
            suspendCancellableCoroutine { continuation ->
                val listener =
                    object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            locationManager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(location)
                        }

                        override fun onProviderDisabled(provider: String) = Unit

                        override fun onProviderEnabled(provider: String) = Unit

                        @Deprecated("Deprecated by Android")
                        @Suppress("OVERRIDE_DEPRECATION")
                        override fun onStatusChanged(
                            provider: String?,
                            status: Int,
                            extras: Bundle?,
                        ) = Unit
                    }
                locationManager.requestSingleUpdate(
                    LocationManager.GPS_PROVIDER,
                    listener,
                    Looper.getMainLooper(),
                )
                continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
            }

        @RequiresApi(Build.VERSION_CODES.R)
        @Suppress("MissingPermission")
        private suspend fun awaitCurrentGps(): Location? =
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                locationManager.getCurrentLocation(
                    LocationManager.GPS_PROVIDER,
                    cancellationSignal,
                    ContextCompat.getMainExecutor(context),
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
            }

        private companion object {
            const val CURRENT_LOCATION_TIMEOUT_MS = 10_000L
        }
    }
