package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.NetworkUtils
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.groupId
import com.locationjoystick.core.model.role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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

/** Advertising (leader) and the follower jobs exclude each other, so one value holds whichever is active. */
internal sealed interface GroupModeState {
    data object Idle : GroupModeState

    data class Advertising(
        val groupId: String,
    ) : GroupModeState

    data class RestoringFollower(
        val job: Job,
    ) : GroupModeState

    data class Rediscovering(
        val job: Job,
    ) : GroupModeState
}

private val GroupModeState.job: Job?
    get() =
        when (this) {
            is GroupModeState.RestoringFollower -> job
            is GroupModeState.Rediscovering -> job
            else -> null
        }

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

    @Volatile
    internal var state: GroupModeState = GroupModeState.Idle
        private set

    /** Applies [next]; a null result rejects the transition. Cancels the job of a state that is left. */
    @Synchronized
    private fun transition(next: (GroupModeState) -> GroupModeState?): Boolean {
        val current = state
        val new = next(current)
        if (new == null) {
            Log.w(TAG, "ignored group mode transition from $current")
            return false
        }
        state = new
        current.job?.takeIf { it !== new.job }?.cancel()
        return true
    }

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
                            transition { GroupModeState.Advertising(groupId) }
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
        val advertising = state as? GroupModeState.Advertising ?: return
        transition { GroupModeState.Idle }
        groupNsdManager.stopAdvertising(advertising.groupId)
    }

    fun rediscoverFollowerGroup(groupId: String) {
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
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
        startJob(job, GroupModeState::Rediscovering)
    }

    /**
     * Starts an async follower restoration job with exponential backoff + jitter.
     * On device boot (START_STICKY), the leader may not have advertised yet, so this job
     * retries NSD discovery over a ~60-second window rather than giving up immediately.
     * Automatically stops on success, exhaustion, or [stopFollowerRestoration].
     */
    fun startFollowerRestoration(groupId: String) {
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
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
        startJob(job, GroupModeState::RestoringFollower)
    }

    private fun startJob(
        job: Job,
        wrap: (Job) -> GroupModeState,
    ) {
        if (transition { if (it is GroupModeState.Advertising) null else wrap(job) }) job.start() else job.cancel()
    }

    /** Cancels any in-flight follower restoration or rediscovery job. */
    fun stopFollowerRestoration() {
        transition { if (it.job != null) GroupModeState.Idle else it }
    }
}
