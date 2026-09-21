package com.locationjoystick.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.locationjoystick.core.common.constants.AppConstants

private val CHANNEL_ID = AppConstants.NotificationConstants.CHANNEL_ID_ACTIVE
private val CHANNEL_ID_MINIMIZED = AppConstants.NotificationConstants.CHANNEL_ID_ACTIVE_MINIMIZED
private val CHANNEL_ID_PERM_ERROR = AppConstants.NotificationConstants.CHANNEL_ID_PERMISSION_ERROR

internal enum class NotificationAction {
    STOP,
    PAUSE,
    RESUME,
    NAV_MAP,
    NAV_FAVORITES,
}

internal data class ActionSpec(
    @param:StringRes val labelRes: Int,
    val action: NotificationAction,
)

internal fun selectNotificationActions(
    replayActive: Boolean,
    replayPaused: Boolean,
): List<ActionSpec> =
    when {
        !replayActive -> {
            listOf(
                ActionSpec(R.string.notification_action_stop, NotificationAction.STOP),
                ActionSpec(R.string.notification_action_map, NotificationAction.NAV_MAP),
                ActionSpec(R.string.notification_action_favorites, NotificationAction.NAV_FAVORITES),
            )
        }

        replayPaused -> {
            listOf(
                ActionSpec(R.string.notification_action_stop, NotificationAction.STOP),
                ActionSpec(R.string.notification_action_resume, NotificationAction.RESUME),
                ActionSpec(R.string.notification_action_map, NotificationAction.NAV_MAP),
            )
        }

        else -> {
            listOf(
                ActionSpec(R.string.notification_action_stop, NotificationAction.STOP),
                ActionSpec(R.string.notification_action_pause, NotificationAction.PAUSE),
                ActionSpec(R.string.notification_action_map, NotificationAction.NAV_MAP),
            )
        }
    }

private fun activityPendingIntent(
    context: Context,
    requestCode: Int,
    extraKey: String,
): PendingIntent? {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
    return PendingIntent.getActivity(
        context,
        requestCode,
        launchIntent.apply {
            putExtra(extraKey, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

internal fun createMockLocationNotificationChannels(context: Context) {
    val channel =
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name_active),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_desc_active)
            setShowBadge(false)
        }
    val minimizedChannel =
        NotificationChannel(
            CHANNEL_ID_MINIMIZED,
            context.getString(R.string.notification_channel_name_active_minimized),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.notification_channel_desc_active_minimized)
            setShowBadge(false)
        }
    val errorChannel =
        NotificationChannel(
            CHANNEL_ID_PERM_ERROR,
            context.getString(R.string.notification_channel_name_permission_error),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_desc_permission_error)
        }
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(channel)
    notificationManager.createNotificationChannel(minimizedChannel)
    notificationManager.createNotificationChannel(errorChannel)
}

internal fun notificationChannelId(hideNotification: Boolean): String = if (hideNotification) CHANNEL_ID_MINIMIZED else CHANNEL_ID

internal fun buildMockLocationNotification(
    context: Context,
    replayActive: Boolean = false,
    replayPaused: Boolean = false,
    hideNotification: Boolean = false,
): Notification {
    val openAppIntent =
        context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.let { intent ->
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

    val stopPendingIntent =
        PendingIntent.getService(
            context,
            1,
            Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    val pausePendingIntent =
        PendingIntent.getService(
            context,
            4,
            Intent(context, MockLocationService::class.java).apply {
                action = AppConstants.ServiceConstants.ACTION_ROUTE_REPLAY_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    val resumePendingIntent =
        PendingIntent.getService(
            context,
            5,
            Intent(context, MockLocationService::class.java).apply {
                action = AppConstants.ServiceConstants.ACTION_ROUTE_REPLAY_RESUME
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    val mapPendingIntent = activityPendingIntent(context, 2, AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_MAP)
    val favoritesPendingIntent =
        activityPendingIntent(context, 3, AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_FAVORITES)

    val builder =
        NotificationCompat
            .Builder(context, notificationChannelId(hideNotification))
            .setContentTitle(context.getString(R.string.notification_title_active))
            .setContentText(context.getString(R.string.notification_text_active))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)

    for (action in selectNotificationActions(replayActive, replayPaused)) {
        val pendingIntent =
            when (action.action) {
                NotificationAction.STOP -> stopPendingIntent
                NotificationAction.PAUSE -> pausePendingIntent
                NotificationAction.RESUME -> resumePendingIntent
                NotificationAction.NAV_MAP -> mapPendingIntent
                NotificationAction.NAV_FAVORITES -> favoritesPendingIntent
            }
        if (pendingIntent != null) {
            builder.addAction(0, context.getString(action.labelRes), pendingIntent)
        }
    }

    return builder.build()
}

internal fun postMockLocationPermissionErrorNotification(context: Context) {
    val openAppIntent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    val notification =
        NotificationCompat
            .Builder(context, CHANNEL_ID_PERM_ERROR)
            .setContentTitle(context.getString(R.string.notification_title_permission_error))
            .setContentText(context.getString(R.string.notification_text_permission_error))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .build()
    context
        .getSystemService(NotificationManager::class.java)
        .notify(AppConstants.NotificationConstants.ID_PERMISSION_ERROR, notification)
}
