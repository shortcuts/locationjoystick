package com.locationjoystick.core.data

import android.content.Intent

/**
 * Single start path for `MockLocationService`. Android 14+ throws when a location-type foreground
 * service starts without location permission, so the check must run before the start, not inside it.
 */
interface MockLocationServiceStarter {
    fun start(intent: Intent)
}
