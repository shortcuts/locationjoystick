package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.NetworkUtils
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.model.GroupRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

private const val TAG = "MockLocationService"

/**
 * Owns leader advertising and the follower restoration and rediscovery jobs. Communicates back to
 * the service via lambdas: [enterFollowerMode], [exitFollowerMode] and [onLeaderExited].
 */
internal class GroupModeController(
    private val scope: CoroutineScope,
    private val leaderSyncServer: LeaderSyncServer,
    private val controlApiRoutes: ControlApiRoutes,
    private val contentApiRoutes: ContentApiRoutes,
    private val groupNsdManager: NsdCodeManager,
    private val groupRepository: GroupRepository,
    private val enterFollowerMode: (host: String, port: Int, groupId: String) -> Unit,
    private val exitFollowerMode: () -> Unit,
    private val onLeaderExited: () -> Unit,
) {
    private val leaderModeMutex = Mutex()
    private var advertisedGroupId: String? = null

    /** Async job for follower restoration with NSD discovery retries on device boot. */
    private var followerRestorationJob: Job? = null
    private var followerRediscoveryJob: Job? = null

    fun enterLeaderMode(groupId: String) {
        stopFollowerRestoration()
        scope.launch {
            try {
                val host =
                    NetworkUtils.getLocalIpAddress() ?: run {
                        Log.e(TAG, "Cannot determine local IP — ensure Wi-Fi is connected")
                        groupRepository.emitNoLanAddress()
                        return@launch
                    }
                val port =
                    leaderModeMutex.withLock {
                        if (!leaderSyncServer.isRunning) {
                            val newPort = leaderSyncServer.start(groupId)
                            groupNsdManager.startAdvertising(code = groupId, port = newPort)
                            advertisedGroupId = groupId
                            newPort
                        } else {
                            leaderSyncServer.currentPort
                        }
                    }
                controlApiRoutes.install()
                contentApiRoutes.install()
                // Always call createGroup to ensure the VM picks up the current host:port
                // (covers both fresh start and idempotent re-entry when already running).
                groupRepository.createGroup(host = host, port = port, groupId = groupId)
                Log.i(TAG, "Entered LEADER mode: $groupId at $host:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start leader mode", e)
            }
        }
    }

    fun exitLeaderMode() {
        leaderSyncServer.stop()
        stopAdvertising()
        Log.i(TAG, "Exited LEADER mode")
        onLeaderExited()
    }

    fun stopAdvertising() {
        advertisedGroupId?.let(groupNsdManager::stopAdvertising)
        advertisedGroupId = null
    }

    fun rediscoverFollowerGroup(groupId: String) {
        followerRediscoveryJob =
            scope.launch {
                var resolved: Pair<String, Int>? = null
                for (attempt in 0..AppConstants.SyncConstants.NSD_REDISCOVERY_RETRY_COUNT) {
                    resolved = groupNsdManager.discoverByCode(groupId)
                    if (resolved != null) break
                    Log.w(TAG, "NSD re-discovery attempt $attempt failed for group $groupId")
                }
                // Discovery may not be cancellable: the user may have left or joined another group meanwhile.
                val state = groupRepository.groupState.first()
                if (state.role != GroupRole.FOLLOWER || state.groupId != groupId) return@launch
                if (resolved != null) {
                    val (newHost, newPort) = resolved
                    Log.i(TAG, "Re-discovered group $groupId at $newHost:$newPort — reconnecting")
                    enterFollowerMode(newHost, newPort, groupId)
                } else {
                    Log.w(TAG, "NSD re-discovery exhausted for group $groupId — leaving group")
                    // exitFollowerMode cancels this job, which would abort the suspending leaveGroup.
                    withContext(NonCancellable) {
                        exitFollowerMode()
                        groupRepository.leaveGroup()
                        groupRepository.emitGroupLost()
                    }
                }
            }
    }

    /**
     * Starts an async follower restoration job with exponential backoff + jitter.
     * On device boot (START_STICKY), the leader may not have advertised yet, so this job
     * retries NSD discovery over a ~60-second window rather than giving up immediately.
     * Automatically stops on success, exhaustion, or [stopFollowerRestoration].
     */
    fun startFollowerRestoration(groupId: String) {
        stopFollowerRestoration()
        followerRestorationJob =
            scope.launch {
                val maxAttempts = AppConstants.FollowerRestorationConstants.MAX_RETRY_ATTEMPTS
                var delayMs = AppConstants.FollowerRestorationConstants.INITIAL_RETRY_DELAY_MS
                val maxDelayMs = AppConstants.FollowerRestorationConstants.MAX_RETRY_DELAY_MS
                val jitterRangeMs = AppConstants.FollowerRestorationConstants.RETRY_JITTER_MS

                for (attempt in 0 until maxAttempts) {
                    if (!isActive) return@launch
                    try {
                        val resolved = groupNsdManager.discoverByCode(groupId)
                        if (resolved != null) {
                            val (host, port) = resolved
                            Log.i(TAG, "Follower restoration succeeded on attempt $attempt for group $groupId at $host:$port")
                            enterFollowerMode(host, port, groupId)
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Follower restoration attempt $attempt failed for group $groupId", e)
                    }

                    if (attempt < maxAttempts - 1) {
                        val jitterMs = Random.nextLong(-jitterRangeMs, jitterRangeMs)
                        delay(delayMs + jitterMs)
                        delayMs = minOf(delayMs * 2, maxDelayMs)
                    }
                }
                Log.w(TAG, "Follower restoration exhausted after $maxAttempts attempts for group $groupId — staying idle")
            }
    }

    /** Cancels any in-flight follower restoration or rediscovery job. */
    fun stopFollowerRestoration() {
        followerRestorationJob?.cancel()
        followerRestorationJob = null
        followerRediscoveryJob?.cancel()
        followerRediscoveryJob = null
    }
}
