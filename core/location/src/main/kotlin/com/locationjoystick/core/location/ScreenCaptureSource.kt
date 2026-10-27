package com.locationjoystick.core.location

import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.GrayImage
import com.locationjoystick.core.model.ReconnectStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between [ScreenAccessibilityService] (in feature:widget:impl) and consumers
 * (the automatic fit). The service binds/unbinds itself; consumers call
 * [captureGrayFrame] which delegates to the live service instance. It also carries the last
 * reconnect check ([reconnectStatus]) to the widget debug view, in memory only.
 */
@Singleton
class ScreenCaptureSource
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        private val service = MutableStateFlow<ScreenAccessibilityServiceBridge?>(null)

        fun bind(svc: ScreenAccessibilityServiceBridge) {
            service.value = svc
        }

        fun unbind() {
            service.value = null
            reportReconnect(null)
        }

        private val _reconnectStatus = MutableStateFlow<ReconnectStatus?>(null)
        val reconnectStatus: StateFlow<ReconnectStatus?> = _reconnectStatus.asStateFlow()

        /** Null clears the line (loop stopped or service gone). */
        fun reportReconnect(status: ReconnectStatus?) {
            _reconnectStatus.value = status
        }

        /**
         * Every switch that lets a tap-to-walk frame exist: disclosure accepted, Tap to Walk on for the guided
         * path, service bound. AutoFitCoordinator drops its crops when this turns false (storage and consent).
         */
        val captureAllowed: Flow<Boolean> =
            combine(
                settingsRepository.getAccessibilityDisclosureAccepted(),
                settingsRepository.getTapToWalkOverlayEnabled(),
                settingsRepository.getGuidedPath(),
                service,
            ) { accepted, on, path, svc -> accepted && on && path.showsTapToWalk && svc != null }

        /**
         * One 8-bit luma screenshot for the automatic tap-to-walk fit, or null (unbound, disclosure
         * not accepted, game not in front, or the screenshot was rejected). Play's Accessibility API
         * policy: no screenshot may be taken before the user accepts the in-app disclosure, and the
         * disclosure names the game in front as the only time frames are taken. The Tap to Walk
         * switch is part of the gate so a frame never exists while the feature is off. Android's own
         * accessibility settings can enable the service without the app ever being opened, so every
         * check lives here rather than at each call site. No retry, the 3 s cadence already
         * tolerates a missed frame.
         */
        suspend fun captureGrayFrame(): GrayImage? {
            if (!captureAllowed.first()) return null
            val svc = service.value ?: return null
            if (!svc.gameInFront) return null
            return svc.captureGrayFrame()
        }
    }

/** Minimal interface exposed by ScreenAccessibilityService to avoid a circular module dependency. */
interface ScreenAccessibilityServiceBridge {
    /** Screen on and the game window in front: the only state in which a frame may be taken. */
    val gameInFront: Boolean

    suspend fun captureGrayFrame(): GrayImage?
}
