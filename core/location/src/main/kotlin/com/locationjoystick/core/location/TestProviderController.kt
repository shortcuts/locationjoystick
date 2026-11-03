package com.locationjoystick.core.location

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.util.Log

private const val TAG = "MockLocationService"

private class ProviderSpec(
    val name: String,
    val powerUsage: Int,
    val requiresNetwork: Boolean = false,
    val satelliteExtras: Boolean = false,
)

/**
 * Owns the test provider registrations and pushes fixes to them. Calls [onError] when Android refuses gps.
 * Apps read the network and fused providers directly, so a gps-only mock leaks the real position.
 */
internal class TestProviderController(
    private val locationManager: LocationManager,
    private val onError: () -> Unit,
    sdkInt: Int = Build.VERSION.SDK_INT,
) {
    @Volatile var isAdded = false
        private set

    private val gps = ProviderSpec(LocationManager.GPS_PROVIDER, ProviderProperties.POWER_USAGE_HIGH, satelliteExtras = true)
    private val extras =
        buildList {
            add(ProviderSpec(LocationManager.NETWORK_PROVIDER, ProviderProperties.POWER_USAGE_LOW, requiresNetwork = true))
            // FUSED_PROVIDER accepts a test provider only from API 31.
            if (sdkInt >= Build.VERSION_CODES.S) add(ProviderSpec(LocationManager.FUSED_PROVIDER, ProviderProperties.POWER_USAGE_LOW))
        }
    private val sdkAtLeastS = sdkInt >= Build.VERSION_CODES.S

    // Specs actually registered; extras that Android refuses stay out so they never receive fixes.
    // Copy-on-write: the update loop iterates while the service may remove from another thread.
    private val active = java.util.concurrent.CopyOnWriteArrayList<ProviderSpec>()

    fun setup() {
        if (isAdded) return
        // A process killed mid-run can leave providers registered under our package, which makes
        // addTestProvider throw IllegalArgumentException. Removing first clears the slot safely.
        for (spec in listOf(gps) + extras) {
            try {
                locationManager.removeTestProvider(spec.name)
                Log.i(TAG, "Removed stale test provider ${spec.name} before re-registering")
            } catch (_: IllegalArgumentException) {
                // Not registered — nothing to clear
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error clearing stale test provider ${spec.name}", e)
            }
        }

        try {
            register(gps)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to add test provider — another app may hold the slot", e)
            onError()
            return
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing mock location permission", e)
            onError()
            return
        }
        active.clear()
        active.add(gps)
        // A refused extra must not stop spoofing: gps already proves the mock slot works.
        for (spec in extras) {
            try {
                register(spec)
                active.add(spec)
            } catch (e: Exception) {
                Log.w(TAG, "Skipping test provider ${spec.name}", e)
                try {
                    locationManager.removeTestProvider(spec.name)
                } catch (cleanup: Exception) {
                    Log.w(TAG, "Could not remove test provider ${spec.name}", cleanup)
                }
            }
        }
        isAdded = true
        Log.i(TAG, "Test provider registered")
    }

    // Lint cannot see the API 31 guard: sdkAtLeastS comes from the injected sdkInt.
    @SuppressLint("NewApi")
    private fun register(spec: ProviderSpec) {
        if (sdkAtLeastS) {
            val properties =
                ProviderProperties
                    .Builder()
                    .apply {
                        setHasAltitudeSupport(true)
                        setHasSpeedSupport(true)
                        setHasBearingSupport(true)
                        setHasNetworkRequirement(spec.requiresNetwork)
                        setPowerUsage(spec.powerUsage)
                        setAccuracy(ProviderProperties.ACCURACY_FINE)
                    }.build()
            locationManager.addTestProvider(spec.name, properties)
        } else {
            addLegacyTestProvider(spec)
        }
        locationManager.setTestProviderEnabled(spec.name, true)
    }

    // Pre-31 devices lack ProviderProperties.Builder + the ProviderProperties addTestProvider
    // overload — same property values as the API 31+ Builder path above.
    @Suppress("DEPRECATION", "InlinedApi")
    private fun addLegacyTestProvider(spec: ProviderSpec) {
        locationManager.addTestProvider(
            spec.name,
            spec.requiresNetwork,
            false,
            false,
            false,
            true,
            true,
            true,
            spec.powerUsage,
            ProviderProperties.ACCURACY_FINE,
        )
    }

    fun remove() {
        if (!isAdded) return
        for (spec in active) {
            try {
                locationManager.removeTestProvider(spec.name)
                Log.i(TAG, "Test provider ${spec.name} removed")
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Test provider ${spec.name} was already removed", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error removing test provider ${spec.name}", e)
            }
        }
        active.clear()
        isAdded = false
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
        for (spec in active) locationManager.setTestProviderLocation(spec.name, buildLocation(spec, fix, nowNanos))
    }

    private fun buildLocation(
        spec: ProviderSpec,
        fix: LocationFix,
        nowNanos: Long,
    ): Location =
        Location(spec.name).apply {
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
            if (spec.satelliteExtras && fix.satelliteCount != null && fix.usedInFixCount != null) {
                val bundle = android.os.Bundle()
                bundle.putInt("satellites", fix.satelliteCount)
                bundle.putInt("usedInFix", fix.usedInFixCount)
                this.extras = bundle
            }
        }
}
