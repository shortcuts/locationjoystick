package com.locationjoystick.core.location

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.locationjoystick.core.common.constants.AppConstants.NotificationConstants
import com.locationjoystick.core.common.constants.AppConstants.ServiceConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ExternalTeleportService"

/** Takes the outside teleport command. Exported, but the manifest ships it disabled. */
@AndroidEntryPoint
class ExternalTeleportService : Service() {
    @Inject lateinit var handler: ExternalTeleportHandler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // The 5-second foreground window starts at onStartCommand, so this comes first.
        // A separate notification ID keeps this service's stop from removing the real notification.
        try {
            createMockLocationNotificationChannels(this)
            val notification = buildMinimizedActiveNotification(this)

            // ServiceCompat ignores the type below API 29, so the inlined constant is safe.
            @SuppressLint("InlinedApi")
            val foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            ServiceCompat.startForeground(this, NotificationConstants.ID_EXTERNAL_TELEPORT, notification, foregroundType)
        } catch (e: Exception) {
            Log.w(TAG, "ignored: foreground start refused", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val action = intent?.action
        if (action != ServiceConstants.ACTION_EXTERNAL_TELEPORT && action != ServiceConstants.ACTION_EXTERNAL_TELEPORT_COMPAT) {
            Log.w(TAG, "ignored: unexpected action $action")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Float extras from `am --ef` fail getDoubleExtra, so read the boxed value of either type.
        val lat = (intent.extras?.get(ServiceConstants.EXTRA_EXTERNAL_LAT) as? Number)?.toDouble()
        val lng = (intent.extras?.get(ServiceConstants.EXTRA_EXTERNAL_LNG) as? Number)?.toDouble()

        scope.launch {
            try {
                handler.handle(lat, lng)
            } catch (e: Exception) {
                Log.e(TAG, "ignored: command failed", e)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
