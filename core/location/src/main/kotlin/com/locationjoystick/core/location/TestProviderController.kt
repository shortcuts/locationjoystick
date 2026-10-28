package com.locationjoystick.core.location

import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.util.Log

private const val TAG = "MockLocationService"

/** Owns the GPS test provider registration and pushes fixes to it. Calls [onError] when Android refuses. */
internal class TestProviderController(
    private val locationManager: LocationManager,
    private val onError: () -> Unit,
) {
    @Volatile var isAdded = false
        private set

    fun setup() {
        if (isAdded) return
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
            isAdded = true
            Log.i(TAG, "Test provider registered")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to add test provider — another app may hold the slot", e)
            onError()
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing mock location permission", e)
            onError()
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

    fun remove() {
        if (!isAdded) return
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            isAdded = false
            Log.i(TAG, "Test provider removed")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Test provider was already removed", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error removing test provider", e)
        }
    }

    fun applyFix(
        fix: LocationFix,
        nowNanos: Long,
    ) {
        try {
            applyToProvider(fix, nowNanos)
        } catch (e: SecurityException) {
            // Not unregistered: remove() would throw the same SecurityException.
            Log.e(TAG, "Mock location app revoked mid-session", e)
            isAdded = false
            onError()
        }
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
                if (fix.hasBearing) bearing = fix.bearing
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = nowNanos
                verticalAccuracyMeters = fix.verticalAccuracyMeters
                if (fix.hasBearing) bearingAccuracyDegrees = fix.bearingAccuracyDegrees
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
}
