package com.locationjoystick.core.location

import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.GrayImage
import com.locationjoystick.core.model.ReconnectStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between [ScreenAccessibilityService] (in feature:widget:impl) and consumers
 * (e.g. the calibration walk). The service binds/unbinds itself; consumers call
 * [captureGrayFrame] which delegates to the live service instance. It also carries the last
 * reconnect check ([reconnectStatus]) to Settings, in memory only.
 */
@Singleton
class ScreenCaptureSource
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        private var service: ScreenAccessibilityServiceBridge? = null

        fun bind(svc: ScreenAccessibilityServiceBridge) {
            service = svc
        }

        fun unbind() {
            service = null
            reportReconnect(null)
        }

        private val _reconnectStatus = MutableStateFlow<ReconnectStatus?>(null)
        val reconnectStatus: StateFlow<ReconnectStatus?> = _reconnectStatus.asStateFlow()

        /** Null clears the line (loop stopped or service gone). */
        fun reportReconnect(status: ReconnectStatus?) {
            _reconnectStatus.value = status
        }

        val isAvailable: Boolean get() = service != null

        /**
         * One 8-bit luma screenshot for the tap-to-walk calibration walk, or null (unbound, disclosure
         * not accepted, or the screenshot was rejected). Play's Accessibility API policy: no
         * screenshot may be taken before the user accepts the in-app disclosure. Android's own
         * accessibility settings can enable the service without the app ever being opened, so the
         * consent check lives here rather than at each call site. No retry, the walk's 3 s cadence
         * already tolerates a missed frame.
         */
        suspend fun captureGrayFrame(): GrayImage? {
            if (!settingsRepository.getAccessibilityDisclosureAccepted().first()) return null
            return service?.captureGrayFrame()
        }
    }

/** Minimal interface exposed by ScreenAccessibilityService to avoid a circular module dependency. */
interface ScreenAccessibilityServiceBridge {
    suspend fun captureGrayFrame(): GrayImage?
}
