package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.GrayImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between [CompassAccessibilityService] (in feature:widget:impl) and consumers
 * (e.g. FloatingWidgetService). The service binds/unbinds itself; consumers call
 * [captureHeading] which delegates to the live service instance.
 */
@Singleton
class CompassHeadingSource
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        private var service: CompassAccessibilityServiceBridge? = null

        fun bind(svc: CompassAccessibilityServiceBridge) {
            service = svc
        }

        fun unbind() {
            service = null
        }

        val isAvailable: Boolean get() = service != null

        /**
         * Play's Accessibility API policy: no screenshot may be taken before the user accepts the
         * in-app disclosure. Android's own accessibility settings can enable the service without
         * the app ever being opened, so the consent check lives here rather than at each call site.
         *
         * Retries a null result (screenshot rejected or needle not found) up to
         * [AppConstants.CompassTrackingConstants.CAPTURE_ATTEMPTS] times; returns null if unbound.
         */
        suspend fun captureHeading(): Float? {
            if (!settingsRepository.getCompassDisclosureAccepted().first()) return null
            repeat(AppConstants.CompassTrackingConstants.CAPTURE_ATTEMPTS) { attempt ->
                val svc = service ?: return null
                svc.captureHeading()?.let { return it }
                if (attempt < AppConstants.CompassTrackingConstants.CAPTURE_ATTEMPTS - 1) {
                    delay(AppConstants.CompassTrackingConstants.CAPTURE_RETRY_DELAY_MS)
                }
            }
            return null
        }

        /**
         * One 8-bit luma screenshot for the tap-to-walk calibration walk, or null (unbound, disclosure
         * not accepted, or the screenshot was rejected). Same consent gate as [captureHeading]; no
         * retry, the walk's 3 s cadence already tolerates a missed frame.
         */
        suspend fun captureGrayFrame(): GrayImage? {
            if (!settingsRepository.getCompassDisclosureAccepted().first()) return null
            return service?.captureGrayFrame()
        }
    }

/** Minimal interface exposed by CompassAccessibilityService to avoid a circular module dependency. */
interface CompassAccessibilityServiceBridge {
    suspend fun captureHeading(): Float?

    suspend fun captureGrayFrame(): GrayImage?
}
