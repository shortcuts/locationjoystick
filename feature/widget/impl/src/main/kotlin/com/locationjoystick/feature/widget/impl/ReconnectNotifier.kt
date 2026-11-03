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
    private val gateClosedStreak = ReconnectGateClosedStreak()

    // Ids we posted and have not cancelled, so the per-check cancels skip the system call when nothing is up.
    private val posted = mutableSetOf<Int>()

    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                AppConstants.NotificationConstants.CHANNEL_ID_RECONNECT_FAILED,
                context.getString(R.string.reconnect_failed_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun onResult(
        result: ReconnectRunResult,
        screenInteractive: Boolean,
    ) {
        // A notice is stale once the accessory is back (ON read) or the control is seen again.
        val accessoryBack = result == ReconnectRunResult.RECONNECTED || result == ReconnectRunResult.NOT_OFF
        if (accessoryBack) cancel(ID_FAILED)
        if (accessoryBack || result == ReconnectRunResult.FAILED) cancel(ID_NOT_FOUND)
        if (result != ReconnectRunResult.GATE_CLOSED) cancel(ID_GATE_CLOSED)
        if (failureStreak.record(result)) {
            post(R.string.reconnect_failed_title, R.string.reconnect_failed_text, ID_FAILED)
        }
        if (notFoundStreak.record(result)) {
            post(R.string.reconnect_not_found_title, R.string.reconnect_not_found_text, ID_NOT_FOUND)
        }
        if (gateClosedStreak.record(result, screenInteractive)) {
            post(
                R.string.reconnect_gate_closed_title,
                R.string.reconnect_gate_closed_text,
                ID_GATE_CLOSED,
                AppConstants.GameConstants.PACKAGE,
            )
        }
    }

    fun reset() {
        failureStreak.reset()
        notFoundStreak.reset()
        gateClosedStreak.reset()
        cancelAll()
    }

    // Unconditional: a notice from a previous process is not in [posted].
    fun cancelAll() {
        posted.clear()
        listOf(ID_FAILED, ID_NOT_FOUND, ID_GATE_CLOSED).forEach { NotificationManagerCompat.from(context).cancel(it) }
    }

    private fun cancel(id: Int) {
        if (posted.remove(id)) NotificationManagerCompat.from(context).cancel(id)
    }

    private fun post(
        @StringRes title: Int,
        @StringRes text: Int,
        id: Int,
        launchPackage: String = context.packageName,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val openApp =
            context.packageManager.getLaunchIntentForPackage(launchPackage)?.let {
                PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
        posted += id
    }

    private companion object {
        const val ID_FAILED = AppConstants.NotificationConstants.ID_RECONNECT_FAILED
        const val ID_NOT_FOUND = AppConstants.NotificationConstants.ID_RECONNECT_NOT_FOUND
        const val ID_GATE_CLOSED = AppConstants.NotificationConstants.ID_RECONNECT_GATE_CLOSED
    }
}
