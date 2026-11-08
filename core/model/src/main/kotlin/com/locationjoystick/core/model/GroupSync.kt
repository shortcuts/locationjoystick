package com.locationjoystick.core.model

enum class GroupRole { NONE, LEADER, FOLLOWER }

sealed interface GroupState {
    data object None : GroupState

    data class Leader(
        val groupId: String,
        val host: String,
        val port: Int,
        val sharingEnabled: Boolean = false,
    ) : GroupState

    data class Follower(
        val groupId: String,
        val host: String,
        val port: Int,
        val followerModeEnabled: Boolean = false,
        val followLeaderTeleports: Boolean = true,
    ) : GroupState
}

val GroupState.role: GroupRole
    get() =
        when (this) {
            GroupState.None -> GroupRole.NONE
            is GroupState.Leader -> GroupRole.LEADER
            is GroupState.Follower -> GroupRole.FOLLOWER
        }

val GroupState.groupId: String?
    get() =
        when (this) {
            GroupState.None -> null
            is GroupState.Leader -> this.groupId
            is GroupState.Follower -> this.groupId
        }

val GroupState.followerModeEnabled: Boolean
    get() = this is GroupState.Follower && this.followerModeEnabled

data class SyncPositionUpdate(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMs: Float,
    val bearing: Float,
    val seq: Long,
    val active: Boolean = true,
    val teleportSeq: Long = 0L,
    val ageMs: Long? = null,
)

data class GroupInvite(
    val host: String,
    val port: Int,
    val groupId: String,
)
