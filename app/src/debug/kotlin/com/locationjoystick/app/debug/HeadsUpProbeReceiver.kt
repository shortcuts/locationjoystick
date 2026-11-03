package com.locationjoystick.app.debug

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** Posts a heads-up notification on demand; `cmd notification post` is silent, so it cannot test the reconnect tap. */
class HeadsUpProbeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Heads-up probe", NotificationManager.IMPORTANCE_HIGH),
        )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Heads-up probe")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setTimeoutAfter(TIMEOUT_MS)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "heads_up_probe"
        const val NOTIFICATION_ID = 9001
        const val TIMEOUT_MS = 6_000L
    }
}
