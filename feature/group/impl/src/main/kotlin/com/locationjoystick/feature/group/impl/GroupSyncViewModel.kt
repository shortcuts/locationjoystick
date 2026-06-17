package com.locationjoystick.feature.group.impl

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.model.GroupInvite
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.GroupState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "GroupSyncViewModel"
private const val QR_SIZE = 512

@HiltViewModel
class GroupSyncViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val groupRepository: GroupRepository,
    ) : ViewModel() {
        private val _groupState = MutableStateFlow(GroupState())
        val groupState: StateFlow<GroupState> = _groupState.asStateFlow()

        private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
        val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        init {
            viewModelScope.launch {
                groupRepository.groupState.collect { state ->
                    _groupState.value = state
                    // Regenerate QR when leader state becomes available and we have no bitmap yet.
                    val host = state.leaderHost
                    val port = state.leaderPort
                    val id = state.groupId
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
        }

        fun createGroup() {
            val groupId = UUID.randomUUID().toString()
            sendServiceAction(AppConstants.ServiceConstants.ACTION_START_LEADER) { intent ->
                intent.putExtra(AppConstants.ServiceConstants.EXTRA_LEADER_GROUP_ID, groupId)
            }
            Log.i(TAG, "Requested group creation: $groupId")
        }

        private fun handlePendingInvite(invite: GroupInvite) {
            viewModelScope.launch {
                // Stop leader server if currently leading before switching to follower.
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
            val host = state.leaderHost ?: return
            val port = state.leaderPort ?: return
            val id = state.groupId ?: return
            viewModelScope.launch {
                groupRepository.setFollowerModeEnabled(enabled)
                if (enabled) {
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

        private fun sendServiceAction(
            action: String,
            configure: ((Intent) -> Unit)? = null,
        ) {
            val intent =
                Intent(action).setClassName(
                    context,
                    AppConstants.ServiceConstants.MOCK_LOCATION_SERVICE_CLASS,
                )
            configure?.invoke(intent)
            context.startService(intent)
        }
    }
