package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.SyncPositionUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FollowerSyncClient"
private const val HTTP_FORBIDDEN = 403

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
        private var consecutiveFailures = 0

        val isPolling: Boolean get() = pollJob?.isActive == true

        private val _followerCount = MutableStateFlow(0)
        val followerCount: StateFlow<Int> = _followerCount.asStateFlow()

        fun startPolling(
            host: String,
            port: Int,
            groupId: String,
            pollIntervalMs: Long = AppConstants.SyncConstants.POLL_INTERVAL_MS,
            onGroupLost: () -> Unit = {},
            onPosition: (lat: Double, lon: Double, speedMs: Float, bearing: Float) -> Unit,
        ) {
            stopPolling()
            lastSeq = -1L
            consecutiveFailures = 0
            pollJob =
                scope.launch {
                    while (isActive) {
                        try {
                            when (val result = fetchPosition(host, port, groupId)) {
                                FetchResult.GroupGone -> {
                                    Log.w(TAG, "Group $groupId no longer recognized by leader $host:$port")
                                    onGroupLost()
                                    break
                                }

                                is FetchResult.Failed -> {
                                    Log.w(TAG, "Poll returned ${result.code} from $host:$port")
                                    consecutiveFailures++
                                    if (consecutiveFailures >= AppConstants.SyncConstants.MAX_CONSECUTIVE_POLL_FAILURES) {
                                        Log.w(TAG, "Leader $host:$port unreachable after $consecutiveFailures attempts — giving up")
                                        onGroupLost()
                                        break
                                    }
                                }

                                is FetchResult.Success -> {
                                    consecutiveFailures = 0
                                    val update = result.update
                                    if (update != null) {
                                        val nowMs = System.currentTimeMillis()
                                        val stale =
                                            nowMs - update.timestamp > AppConstants.SyncConstants.POSITION_STALE_THRESHOLD_MS
                                        if (!stale && update.seq > lastSeq) {
                                            lastSeq = update.seq
                                            onPosition(update.latitude, update.longitude, update.speedMs, update.bearing)
                                        }
                                    }
                                }

                                FetchResult.Empty -> {
                                    consecutiveFailures = 0
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Poll failed", e)
                            consecutiveFailures++
                            if (consecutiveFailures >= AppConstants.SyncConstants.MAX_CONSECUTIVE_POLL_FAILURES) {
                                Log.w(TAG, "Leader $host:$port unreachable after $consecutiveFailures attempts — giving up")
                                onGroupLost()
                                break
                            }
                        }
                        delay(pollIntervalMs)
                    }
                }
            Log.i(TAG, "Polling started: $host:$port")
        }

        suspend fun checkGroupExists(
            host: String,
            port: Int,
            groupId: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    fetchPosition(host, port, groupId) != FetchResult.GroupGone
                } catch (e: Exception) {
                    Log.w(TAG, "Group existence check failed", e)
                    true
                }
            }

        fun stopPolling() {
            pollJob?.cancel()
            pollJob = null
            _followerCount.value = 0
            Log.i(TAG, "Polling stopped")
        }

        private sealed class FetchResult {
            data class Success(
                val update: SyncPositionUpdate?,
            ) : FetchResult()

            data object GroupGone : FetchResult()

            data object Empty : FetchResult()

            /** Non-2xx, non-403 HTTP response — a routine polling failure, not an exceptional one. */
            data class Failed(
                val code: Int,
            ) : FetchResult()
        }

        private fun fetchPosition(
            host: String,
            port: Int,
            groupId: String,
        ): FetchResult {
            val request =
                Request
                    .Builder()
                    .url("http://$host:$port/position?token=$groupId")
                    .get()
                    .build()
            client.newCall(request).execute().use { response ->
                if (response.code == HTTP_FORBIDDEN) {
                    return FetchResult.GroupGone
                }
                if (!response.isSuccessful) {
                    return FetchResult.Failed(response.code)
                }
                val body = response.body?.string() ?: return FetchResult.Empty
                try {
                    _followerCount.value = JSONObject(body).optInt("followers", 0)
                } catch (_: Exception) {
                }
                return FetchResult.Success(parsePosition(body))
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
