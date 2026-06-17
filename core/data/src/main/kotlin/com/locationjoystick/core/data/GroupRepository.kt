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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
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
            val GROUP_FOLLOWER_MODE_ENABLED = booleanPreferencesKey(AppConstants.DataStoreConstants.KEY_GROUP_FOLLOWER_MODE_ENABLED)
            val GROUP_SHARING_ENABLED = booleanPreferencesKey(AppConstants.DataStoreConstants.KEY_GROUP_SHARING_ENABLED)
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
                prefs[Keys.GROUP_FOLLOWER_MODE_ENABLED] = false
                prefs[Keys.GROUP_SHARING_ENABLED] = false
            }
        }

        suspend fun setFollowerModeEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.GROUP_FOLLOWER_MODE_ENABLED] = enabled }
        }

        suspend fun setSharingEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.GROUP_SHARING_ENABLED] = enabled }
        }

        suspend fun leaveGroup() {
            dataStore.edit { prefs ->
                prefs.remove(Keys.GROUP_ROLE)
                prefs.remove(Keys.GROUP_ID)
                prefs.remove(Keys.GROUP_LEADER_HOST)
                prefs.remove(Keys.GROUP_LEADER_PORT)
                prefs.remove(Keys.GROUP_FOLLOWER_MODE_ENABLED)
                prefs.remove(Keys.GROUP_SHARING_ENABLED)
            }
        }

        private val _pendingGroupInvite = MutableSharedFlow<GroupInvite>(replay = 1)
        val pendingGroupInvite = _pendingGroupInvite.asSharedFlow()

        fun setPendingGroupInvite(invite: GroupInvite) {
            _pendingGroupInvite.tryEmit(invite)
        }

        fun consumeGroupInvite() {
            _pendingGroupInvite.resetReplayCache()
        }
    }
