package com.locationjoystick.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.GroupInvite
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.GroupState
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        private object Keys {
            val GROUP_ROLE = stringPreferencesKey(AppConstants.DataStoreConstants.KEY_GROUP_ROLE)
            val GROUP_ID = stringPreferencesKey(AppConstants.DataStoreConstants.KEY_GROUP_ID)
            val GROUP_LEADER_HOST = stringPreferencesKey(AppConstants.DataStoreConstants.KEY_GROUP_LEADER_HOST)
            val GROUP_LEADER_PORT = intPreferencesKey(AppConstants.DataStoreConstants.KEY_GROUP_LEADER_PORT)
            val GROUP_FOLLOWER_MODE_ENABLED =
                booleanPreferencesKey(AppConstants.DataStoreConstants.KEY_GROUP_FOLLOWER_MODE_ENABLED)
            val GROUP_FOLLOW_LEADER_TELEPORTS =
                booleanPreferencesKey(AppConstants.DataStoreConstants.KEY_GROUP_FOLLOW_LEADER_TELEPORTS)
            val GROUP_SHARING_ENABLED = booleanPreferencesKey(AppConstants.DataStoreConstants.KEY_GROUP_SHARING_ENABLED)
            val API_KEY = stringPreferencesKey(AppConstants.DataStoreConstants.KEY_API_KEY)
        }

        val groupState: Flow<GroupState> =
            dataStore.data.map { prefs ->
                val roleStr = prefs[Keys.GROUP_ROLE] ?: GroupRole.NONE.name
                val role =
                    try {
                        GroupRole.valueOf(roleStr)
                    } catch (_: IllegalArgumentException) {
                        GroupRole.NONE
                    }
                GroupState(
                    role = role,
                    groupId = prefs[Keys.GROUP_ID],
                    leaderHost = prefs[Keys.GROUP_LEADER_HOST],
                    leaderPort = prefs[Keys.GROUP_LEADER_PORT],
                    followerModeEnabled = prefs[Keys.GROUP_FOLLOWER_MODE_ENABLED] ?: false,
                    sharingEnabled = prefs[Keys.GROUP_SHARING_ENABLED] ?: false,
                    followLeaderTeleports = prefs[Keys.GROUP_FOLLOW_LEADER_TELEPORTS] ?: true,
                )
            }

        suspend fun createGroup(
            host: String,
            port: Int,
            groupId: String,
        ) {
            dataStore.edit { prefs ->
                prefs[Keys.GROUP_ROLE] = GroupRole.LEADER.name
                prefs[Keys.GROUP_ID] = groupId
                prefs[Keys.GROUP_LEADER_HOST] = host
                prefs[Keys.GROUP_LEADER_PORT] = port
                prefs[Keys.GROUP_SHARING_ENABLED] = true
                prefs[Keys.GROUP_FOLLOWER_MODE_ENABLED] = false
            }
        }

        suspend fun joinGroup(invite: GroupInvite) {
            dataStore.edit { prefs ->
                prefs[Keys.GROUP_ROLE] = GroupRole.FOLLOWER.name
                prefs[Keys.GROUP_ID] = invite.groupId
                prefs[Keys.GROUP_LEADER_HOST] = invite.host
                prefs[Keys.GROUP_LEADER_PORT] = invite.port
                prefs[Keys.GROUP_FOLLOWER_MODE_ENABLED] = true
                prefs[Keys.GROUP_SHARING_ENABLED] = false
            }
        }

        suspend fun setFollowerModeEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.GROUP_FOLLOWER_MODE_ENABLED] = enabled }
        }

        suspend fun setFollowLeaderTeleports(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.GROUP_FOLLOW_LEADER_TELEPORTS] = enabled }
        }

        suspend fun setSharingEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.GROUP_SHARING_ENABLED] = enabled }
        }

        /** The control-API Bearer key. Survives [leaveGroup]; only [regenerateApiKey] replaces it. */
        suspend fun getOrCreateApiKey(): String = dataStore.data.first()[Keys.API_KEY] ?: regenerateApiKey()

        suspend fun regenerateApiKey(): String {
            val chars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            val rng = SecureRandom()
            val key = String(CharArray(AppConstants.SyncConstants.API_KEY_LENGTH) { chars[rng.nextInt(chars.size)] })
            dataStore.edit { it[Keys.API_KEY] = key }
            return key
        }

        /** Follower-only, in-memory: the leader's last-known position, for cooldown/distance UI. Not persisted. */
        private val _leaderPosition = MutableStateFlow<LatLng?>(null)
        val leaderPosition: StateFlow<LatLng?> = _leaderPosition.asStateFlow()

        fun setLeaderPosition(position: LatLng?) {
            _leaderPosition.value = position
        }

        suspend fun leaveGroup() {
            dataStore.edit { prefs ->
                prefs.remove(Keys.GROUP_ROLE)
                prefs.remove(Keys.GROUP_ID)
                prefs.remove(Keys.GROUP_LEADER_HOST)
                prefs.remove(Keys.GROUP_LEADER_PORT)
                prefs.remove(Keys.GROUP_FOLLOWER_MODE_ENABLED)
                prefs.remove(Keys.GROUP_SHARING_ENABLED)
                prefs.remove(Keys.GROUP_FOLLOW_LEADER_TELEPORTS)
            }
            _leaderPosition.value = null
        }

        private val _pendingGroupInvite = MutableSharedFlow<GroupInvite>(replay = 1)
        val pendingGroupInvite = _pendingGroupInvite.asSharedFlow()

        fun setPendingGroupInvite(invite: GroupInvite) {
            _pendingGroupInvite.tryEmit(invite)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun consumeGroupInvite() {
            _pendingGroupInvite.resetReplayCache()
        }

        private val _groupLostEvent = MutableSharedFlow<Unit>()
        val groupLostEvent = _groupLostEvent.asSharedFlow()

        fun emitGroupLost() {
            _groupLostEvent.tryEmit(Unit)
        }

        private val _noLanAddressEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val noLanAddressEvent = _noLanAddressEvent.asSharedFlow()

        fun emitNoLanAddress() {
            _noLanAddressEvent.tryEmit(Unit)
        }

        private val _teleportUnavailableEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val teleportUnavailableEvent = _teleportUnavailableEvent.asSharedFlow()

        fun emitTeleportUnavailable() {
            _teleportUnavailableEvent.tryEmit(Unit)
        }
    }
