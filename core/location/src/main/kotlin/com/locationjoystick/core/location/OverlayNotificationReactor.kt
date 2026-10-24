package com.locationjoystick.core.location

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class NotificationInputs(
    val mode: MockMode,
    val state: MockLocationState,
    val hideNotification: Boolean,
) {
    // Double-guarded: walk-to PAUSED never triggers replayPaused=true
    val replayActive get() = mode == MockMode.ROUTE_REPLAY
    val replayPaused get() = replayActive && state == MockLocationState.PAUSED
}

/**
 * Owns the two settings-reactive collectors that keep the foreground notification and the widget
 * overlay in sync with live setting changes mid-session, extracted from
 * [MockLocationService.observeLocationState] — mirrors the [RealismSettingsState] pattern: both
 * collectors are pure `combine().collect {}` blocks with no other service-internal dependencies
 * beyond posting the notification and starting/stopping the widget overlay service.
 */
internal class OverlayNotificationReactor(
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Mirrors the current hideNotification setting so [MockLocationService.onStartCommand]'s
     * re-post of the notification (e.g. on every joystick-driven ACTION_UPDATE_POSITION) doesn't
     * clobber the minimized channel back to visible.
     */
    @Volatile var hideNotificationSetting: Boolean = false
        private set

    @Volatile private var latest: NotificationInputs? = null

    /**
     * Builds the notification from the last inputs the collector saw. [MockLocationService.onStartCommand]
     * re-posts through this, so a walk tick cannot reset the replay actions to the defaults.
     */
    fun buildCurrent(): Notification {
        val inputs = latest
        return buildMockLocationNotification(
            context,
            replayActive = inputs?.replayActive ?: false,
            replayPaused = inputs?.replayPaused ?: false,
            hideNotification = hideNotificationSetting,
        )
    }

    private val widgetServiceIntent by lazy {
        Intent().setClassName(context.packageName, AppConstants.ServiceConstants.WIDGET_SERVICE_CLASS)
    }

    /** Launches both collectors on [scope]. [spoofState] is the service's own state — used only by
     * the widget-overlay collector; the notification collector reads mode/state from
     * [locationRepository] instead, matching the pre-extraction behavior exactly. */
    fun observe(
        scope: CoroutineScope,
        spoofState: StateFlow<MockLocationState>,
    ) {
        // distinctUntilChanged prevents notify storm during 1 Hz position ticks.
        scope.launch {
            combine(
                locationRepository.currentMode,
                locationRepository.mockLocationState,
                settingsRepository.getHideForegroundNotification(),
            ) { mode, state, hideNotification -> NotificationInputs(mode, state, hideNotification) }
                .distinctUntilChanged()
                .collect { inputs ->
                    hideNotificationSetting = inputs.hideNotification
                    latest = inputs
                    notificationManager.notify(AppConstants.NotificationConstants.ID_ACTIVE, buildCurrent())
                }
        }
        // The RUNNING branch in observeLocationState only reads getHideWidgetOverlay() at the
        // transition to RUNNING, so flipping it mid-session needs this separate live collector
        // (see docs/features/widget.md, "Hiding the Overlay").
        scope.launch {
            combine(spoofState, settingsRepository.getHideWidgetOverlay()) { state, hideWidget -> state to hideWidget }
                .distinctUntilChanged()
                .collect { (state, hideWidget) ->
                    if (!Settings.canDrawOverlays(context)) return@collect
                    when (computeWidgetOverlayAction(state, hideWidget)) {
                        WidgetOverlayAction.START -> context.startService(widgetServiceIntent)
                        WidgetOverlayAction.STOP -> context.stopService(widgetServiceIntent)
                        WidgetOverlayAction.NO_OP -> Unit
                    }
                }
        }
    }
}
