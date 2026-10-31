package com.locationjoystick.core.location

import android.annotation.SuppressLint
import android.os.PowerManager
import android.util.Log

private const val TAG = "MockLocationService"
private const val WAKE_LOCK_TAG = "locationjoystick:spoofing"

// A timeout would end the lock mid-session; release() is the bound (stop, park, destroy, error).

/** Acquires a [PowerManager.PARTIAL_WAKE_LOCK], working around Doze/Adaptive Battery
 * throttling the update loop on some devices (e.g. Android 15 Pixel) when the screen locks.
 * Returns null if acquisition fails — caller falls back to the foreground service alone. */
@SuppressLint("WakelockTimeout")
internal fun acquireSpoofingWakeLock(powerManager: PowerManager): PowerManager.WakeLock? =
    try {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply { acquire() }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to acquire wakelock; falling back to foreground service alone", e)
        null
    }

internal fun releaseSpoofingWakeLock(wakeLock: PowerManager.WakeLock?) {
    wakeLock?.let { if (it.isHeld) it.release() }
}

/** Holds the spoofing wake lock only while actively spoofing (RUNNING/PAUSED). */
internal class SpoofingWakeLockHolder(
    private val powerManager: () -> PowerManager,
) {
    @Volatile private var wakeLock: PowerManager.WakeLock? = null

    fun acquire() {
        if (wakeLock?.isHeld == true) return
        wakeLock = acquireSpoofingWakeLock(powerManager())
    }

    fun release() {
        releaseSpoofingWakeLock(wakeLock)
        wakeLock = null
    }
}
