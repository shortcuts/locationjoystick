package com.locationjoystick.feature.group.impl

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.common.util.RandomCode
import com.locationjoystick.core.common.util.tickerFlow
import com.locationjoystick.core.data.CooldownEngine
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.FollowerSyncClient
import com.locationjoystick.core.location.LeaderSyncServer
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.GroupInvite
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.GroupState
import com.locationjoystick.core.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "GroupSyncViewModel"
private const val QR_SIZE = 512

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class GroupSyncViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val groupRepository: GroupRepository,
        private val groupNsdManager: NsdCodeManager,
        private val leaderSyncServer: LeaderSyncServer,
        private val followerSyncClient: FollowerSyncClient,
        private val settingsRepository: SettingsRepository,
        private val locationRepository: LocationRepository,
    ) : ViewModel() {
        private val _groupState = MutableStateFlow(GroupState())
        val groupState: StateFlow<GroupState> = _groupState.asStateFlow()

        private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
        val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        private val _isDiscovering = MutableStateFlow(false)
        val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

        val hideTeleportFeatures: StateFlow<Boolean> =
            settingsRepository
                .getHideTeleportFeatures()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val followerCount: StateFlow<Int> =
            groupRepository.groupState
                .flatMapLatest { state ->
                    when (state.role) {
                        GroupRole.LEADER -> leaderSyncServer.followerCount
                        GroupRole.FOLLOWER -> followerSyncClient.followerCount
                        GroupRole.NONE -> flowOf(0)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

        val leaderPosition: StateFlow<LatLng?> =
            groupRepository.leaderPosition
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val currentPosition: StateFlow<LatLng?> =
            locationRepository.currentPosition
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Distance/cooldown advisory for "Teleport to leader now" — same clock and math as
         * every other teleport (Favorites, map long-press); see CooldownEngine. */
        val cooldownState: StateFlow<CooldownState> =
            combine(
                settingsRepository.getLastTeleportTime(),
                locationRepository.currentPosition,
                groupRepository.leaderPosition,
                tickerFlow(30_000L),
            ) { teleportTime, currentPos, leaderPos, _ ->
                if (leaderPos == null || currentPos == null) {
                    CooldownState.Ready
                } else {
                    CooldownEngine.computeState(teleportTime, currentPos, leaderPos)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CooldownState.Ready)

        init {
            viewModelScope.launch {
                var leaderRestoreSent = false
                var followerRestoreSent = false
                var followerExistenceChecked = false
                groupRepository.groupState.collect { state ->
                    _groupState.value = state
                    val host = state.leaderHost
                    val port = state.leaderPort
                    val id = state.groupId
                    // On the first emission where we're a leader, check whether the server is
                    // actually running. After a device reboot the service is dead even though
                    // DataStore still says LEADER — the persisted host:port are stale. Restart
                    // the service so it picks a fresh port and writes it back to the repository,
                    // then generate the QR from that updated state instead of the stale one.
                    if (state.role == GroupRole.LEADER && id != null && !leaderRestoreSent) {
                        leaderRestoreSent = true
                        if (!leaderSyncServer.isRunning) {
                            sendServiceAction(AppConstants.ServiceConstants.ACTION_START_LEADER) { intent ->
                                intent.putExtra(AppConstants.ServiceConstants.EXTRA_LEADER_GROUP_ID, id)
                            }
                            return@collect
                        }
                    }
                    // On the first emission where we're a follower with follow mode enabled, check
                    // whether the client is actually polling. After a reboot, the service may have
                    // failed NSD re-discovery silently (leader not yet advertising) and stayed idle
                    // while DataStore still says followerModeEnabled=true. Re-trigger follow mode so
                    // NSD runs again with more time elapsed and the leader is reachable.
                    // Check group existence immediately on entering the screen instead of waiting
                    // for the next poll tick, so a stale/gone group is surfaced right away.
                    if (state.role == GroupRole.FOLLOWER && !followerExistenceChecked &&
                        host != null && port != null && id != null
                    ) {
                        followerExistenceChecked = true
                        if (!followerSyncClient.checkGroupExists(host, port, id)) {
                            Log.w(TAG, "Group $id no longer exists on entry check — leaving group")
                            leaveGroup()
                            return@collect
                        }
                    }
                    if (state.role == GroupRole.FOLLOWER && state.followerModeEnabled && !followerRestoreSent) {
                        followerRestoreSent = true
                        if (!followerSyncClient.isPolling) {
                            setFollowerModeEnabled(true)
                            return@collect
                        }
                    }
                    if (state.role == GroupRole.LEADER && _qrBitmap.value == null &&
                        host != null && port != null && id != null
                    ) {
                        generateQrCode(host, port, id)
                    }
                }
            }
            viewModelScope.launch {
                groupRepository.pendingGroupInvite.collect { invite ->
                    handlePendingInvite(invite)
                }
            }
            viewModelScope.launch {
                groupRepository.groupLostEvent.collect {
                    _errorMessage.value = "Disconnected from group — leader is no longer reachable"
                }
            }
            viewModelScope.launch {
                groupRepository.teleportUnavailableEvent.collect {
                    _errorMessage.value = "Leader position not yet known — try again in a moment"
                }
            }
        }

        fun createGroup() {
            val code = RandomCode.generate(AppConstants.SyncConstants.GROUP_CODE_LENGTH)
            sendServiceAction(AppConstants.ServiceConstants.ACTION_START_LEADER) { intent ->
                intent.putExtra(AppConstants.ServiceConstants.EXTRA_LEADER_GROUP_ID, code)
            }
            Log.i(TAG, "Requested group creation with code: $code")
        }

        fun joinByCode(code: String) {
            val normalized = code.uppercase().trim()
            if (normalized.length != AppConstants.SyncConstants.GROUP_CODE_LENGTH) {
                _errorMessage.value = "Code must be ${AppConstants.SyncConstants.GROUP_CODE_LENGTH} characters"
                return
            }
            viewModelScope.launch {
                _isDiscovering.value = true
                val result = groupNsdManager.discoverByCode(normalized)
                _isDiscovering.value = false
                if (result != null) {
                    val (host, port) = result
                    handlePendingInvite(GroupInvite(host = host, port = port, groupId = normalized))
                } else {
                    _errorMessage.value = "No group found for code $normalized"
                }
            }
        }

        fun joinViaScannedUrl(url: String) {
            val invite = parseGroupUrl(url)
            if (invite == null) {
                _errorMessage.value = "Invalid group QR code"
                return
            }
            viewModelScope.launch { handlePendingInvite(invite) }
        }

        private fun handlePendingInvite(invite: GroupInvite) {
            viewModelScope.launch {
                if (_groupState.value.role == GroupRole.LEADER) {
                    sendServiceAction(AppConstants.ServiceConstants.ACTION_EXIT_LEADER)
                }
                groupRepository.joinGroup(invite)
                groupRepository.consumeGroupInvite()
                _qrBitmap.value = null
                Log.i(TAG, "Joined group ${invite.groupId} at ${invite.host}:${invite.port}")
            }
        }

        fun setFollowerModeEnabled(enabled: Boolean) {
            val state = _groupState.value
            if (state.role != GroupRole.FOLLOWER) return
            val id = state.groupId ?: return
            viewModelScope.launch {
                groupRepository.setFollowerModeEnabled(enabled)
                if (enabled) {
                    // The leader's host:port may have changed since the invite was saved (e.g. the
                    // leader's device restarted and got a new OS-assigned port) — re-resolve via NSD
                    // rather than trusting the persisted host:port.
                    _isDiscovering.value = true
                    val resolved = groupNsdManager.discoverByCode(id)
                    _isDiscovering.value = false
                    if (resolved == null) {
                        _errorMessage.value = "No group found for code $id"
                        groupRepository.setFollowerModeEnabled(false)
                        return@launch
                    }
                    val (host, port) = resolved
                    sendServiceAction(AppConstants.ServiceConstants.ACTION_ENTER_FOLLOWER) { intent ->
                        intent.putExtra(AppConstants.ServiceConstants.EXTRA_FOLLOWER_HOST, host)
                        intent.putExtra(AppConstants.ServiceConstants.EXTRA_FOLLOWER_PORT, port)
                        intent.putExtra(AppConstants.ServiceConstants.EXTRA_FOLLOWER_GROUP_ID, id)
                    }
                } else {
                    sendServiceAction(AppConstants.ServiceConstants.ACTION_EXIT_FOLLOWER)
                }
            }
        }

        fun teleportToLeaderNow() {
            if (_groupState.value.role != GroupRole.FOLLOWER) return
            sendServiceAction(AppConstants.ServiceConstants.ACTION_FOLLOWER_TELEPORT)
        }

        fun setSharingEnabled(enabled: Boolean) {
            viewModelScope.launch {
                groupRepository.setSharingEnabled(enabled)
            }
        }

        fun leaveGroup() {
            val state = _groupState.value
            viewModelScope.launch {
                when (state.role) {
                    GroupRole.FOLLOWER -> {
                        if (state.followerModeEnabled) {
                            sendServiceAction(AppConstants.ServiceConstants.ACTION_EXIT_FOLLOWER)
                        }
                    }

                    GroupRole.LEADER -> {
                        sendServiceAction(AppConstants.ServiceConstants.ACTION_EXIT_LEADER)
                    }

                    GroupRole.NONE -> {
                        Unit
                    }
                }
                groupRepository.leaveGroup()
                _qrBitmap.value = null
            }
        }

        fun clearError() {
            _errorMessage.value = null
        }

        fun regenerateQr() {
            val state = _groupState.value
            if (state.role != GroupRole.LEADER) return
            val host = state.leaderHost ?: return
            val port = state.leaderPort ?: return
            val id = state.groupId ?: return
            generateQrCode(host, port, id)
        }

        private fun generateQrCode(
            host: String,
            port: Int,
            groupId: String,
        ) {
            val url = "locationjoystick://group?host=$host&port=$port&id=$groupId"
            try {
                val matrix = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE)
                val pixels = IntArray(QR_SIZE * QR_SIZE)
                for (y in 0 until QR_SIZE) {
                    for (x in 0 until QR_SIZE) {
                        pixels[y * QR_SIZE + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    }
                }
                _qrBitmap.value = Bitmap.createBitmap(pixels, QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate QR code", e)
            }
        }

        private fun parseGroupUrl(url: String): GroupInvite? {
            return try {
                val uri = Uri.parse(url)
                if (uri.scheme != "locationjoystick" || uri.host != "group") return null
                val host = uri.getQueryParameter("host") ?: return null
                val port = uri.getQueryParameter("port")?.toIntOrNull() ?: return null
                val id = uri.getQueryParameter("id") ?: return null
                GroupInvite(host = host, port = port, groupId = id)
            } catch (e: Exception) {
                null
            }
        }

        private fun sendServiceAction(
            action: String,
            configure: ((Intent) -> Unit)? = null,
        ) {
            val intent = Intent(context, MockLocationService::class.java).apply { this.action = action }
            configure?.invoke(intent)
            context.startService(intent)
        }
    }
