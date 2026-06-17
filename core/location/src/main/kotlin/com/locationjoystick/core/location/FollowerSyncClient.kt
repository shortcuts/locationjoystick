package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.SyncPositionUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FollowerSyncClient"

@Singleton
class FollowerSyncClient
    @Inject
    constructor() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(AppConstants.SyncConstants.POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(AppConstants.SyncConstants.POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()

        private var pollJob: Job? = null
        private var lastSeq: Long = -1L

        private val _followerCount = MutableStateFlow(0)
        val followerCount: StateFlow<Int> = _followerCount.asStateFlow()

        fun startPolling(
            host: String,
            port: Int,
            groupId: String,
            pollIntervalMs: Long = AppConstants.SyncConstants.POLL_INTERVAL_MS,
            onPosition: (lat: Double, lon: Double, speedMs: Float, bearing: Float) -> Unit,
        ) {
            stopPolling()
            lastSeq = -1L
            pollJob =
                scope.launch {
                    while (isActive) {
                        try {
                            val update = fetchPosition(host, port, groupId)
                            if (update != null) {
                                val nowMs = System.currentTimeMillis()
                                val stale = nowMs - update.timestamp > AppConstants.SyncConstants.POSITION_STALE_THRESHOLD_MS
                                if (!stale && update.seq > lastSeq) {
                                    lastSeq = update.seq
                                    onPosition(update.latitude, update.longitude, update.speedMs, update.bearing)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Poll failed", e)
                        }
                        delay(pollIntervalMs)
                    }
                }
            Log.i(TAG, "Polling started: $host:$port")
        }

        fun stopPolling() {
            pollJob?.cancel()
            pollJob = null
            _followerCount.value = 0
            Log.i(TAG, "Polling stopped")
        }

        private fun fetchPosition(
            host: String,
            port: Int,
            groupId: String,
        ): SyncPositionUpdate? {
            val request =
                Request
                    .Builder()
                    .url("http://$host:$port/position?token=$groupId")
                    .get()
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Poll returned ${response.code} from $host:$port")
                    return null
                }
                val body = response.body?.string() ?: return null
                try {
                    _followerCount.value = JSONObject(body).optInt("followers", 0)
                } catch (_: Exception) {}
                return parsePosition(body)
            }
        }

        private fun parsePosition(json: String): SyncPositionUpdate? =
            try {
                val obj = JSONObject(json)
                SyncPositionUpdate(
                    timestamp = obj.getLong("ts"),
                    latitude = obj.getDouble("lat"),
                    longitude = obj.getDouble("lon"),
                    speedMs = obj.optDouble("speedMs", 0.0).toFloat(),
                    bearing = obj.optDouble("bearing", 0.0).toFloat(),
                    seq = obj.getLong("seq"),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse position JSON", e)
                null
            }
    }
