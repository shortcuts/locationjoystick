package com.locationjoystick.core.model

enum class GroupRole { NONE, LEADER, FOLLOWER }

data class GroupState(
    val role: GroupRole = GroupRole.NONE,
    val groupId: String? = null,
    val leaderHost: String? = null,
    val leaderPort: Int? = null,
    val followerModeEnabled: Boolean = false,
    val sharingEnabled: Boolean = false,
    val followLeaderTeleports: Boolean = true,
)

data class SyncPositionUpdate(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMs: Float,
    val bearing: Float,
    val seq: Long,
    val active: Boolean = true,
    val teleportSeq: Long = 0L,
)

data class GroupInvite(
    val host: String,
    val port: Int,
    val groupId: String,
)
