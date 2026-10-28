package com.locationjoystick.feature.widget.impl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.ReconnectRunResult

/** Turns each reconnect result into posts and cancels. The streaks live here, in memory only: a process restart at worst delays one notification. */
internal class ReconnectNotifier(
    private val context: Context,
) {
    private val failureStreak = ReconnectFailureStreak()
    private val notFoundStreak = ReconnectNotFoundStreak()

    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                AppConstants.NotificationConstants.CHANNEL_ID_RECONNECT_FAILED,
                context.getString(R.string.reconnect_failed_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun onResult(result: ReconnectRunResult) {
        // A notice is stale once the accessory is back (ON read) or the control is seen again.
        val accessoryBack = result == ReconnectRunResult.RECONNECTED || result == ReconnectRunResult.NOT_OFF
        if (accessoryBack) cancel(ID_FAILED)
        if (accessoryBack || result == ReconnectRunResult.FAILED) cancel(ID_NOT_FOUND)
        if (failureStreak.record(result)) {
            post(R.string.reconnect_failed_title, R.string.reconnect_failed_text, ID_FAILED)
        }
        if (notFoundStreak.record(result)) {
            post(R.string.reconnect_not_found_title, R.string.reconnect_not_found_text, ID_NOT_FOUND)
        }
    }

    fun reset() {
        failureStreak.reset()
        notFoundStreak.reset()
        cancelAll()
    }

    fun cancelAll() {
        cancel(ID_FAILED)
        cancel(ID_NOT_FOUND)
    }

    private fun cancel(id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun post(
        @StringRes title: Int,
        @StringRes text: Int,
        id: Int,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val openApp =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
        val notification =
            NotificationCompat
                .Builder(context, AppConstants.NotificationConstants.CHANNEL_ID_RECONNECT_FAILED)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(context.getString(title))
                .setContentText(context.getString(text))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        // areNotificationsEnabled() above covers the POST_NOTIFICATIONS check lint cannot trace.
        @Suppress("MissingPermission")
        manager.notify(id, notification)
    }

    private companion object {
        const val ID_FAILED = AppConstants.NotificationConstants.ID_RECONNECT_FAILED
        const val ID_NOT_FOUND = AppConstants.NotificationConstants.ID_RECONNECT_NOT_FOUND
    }
}
