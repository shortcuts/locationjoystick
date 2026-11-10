package com.locationjoystick.feature.group.impl

import com.locationjoystick.core.model.GroupRole

/**
 * One-shot restore checks per role. A role change resets the checks, so rejoining a group checks
 * again. The leader's API key lives in the leader states only, so it cannot outlive the role.
 */
internal sealed interface RestoreState {
    data object Idle : RestoreState

    data class LeaderPending(
        val apiKey: String? = null,
    ) : RestoreState

    data class LeaderDone(
        val apiKey: String?,
    ) : RestoreState

    data object FollowerPending : RestoreState

    data object FollowerExistenceChecked : RestoreState

    data object FollowerDone : RestoreState
}

internal sealed interface RestoreEvent {
    data class RoleChanged(
        val role: GroupRole,
    ) : RestoreEvent

    data class ApiKeyLoaded(
        val key: String,
    ) : RestoreEvent

    data object LeaderChecked : RestoreEvent

    data object ExistenceChecked : RestoreEvent

    data object FollowerRestoreChecked : RestoreEvent
}

/** Returns the next state. An illegal event returns [this]. */
internal fun RestoreState.reduce(event: RestoreEvent): RestoreState =
    when (event) {
        is RestoreEvent.RoleChanged ->
            when (event.role) {
                GroupRole.NONE -> RestoreState.Idle
                GroupRole.LEADER ->
                    if (this is RestoreState.LeaderPending ||
                        this is RestoreState.LeaderDone
                    ) {
                        this
                    } else {
                        RestoreState.LeaderPending()
                    }
                GroupRole.FOLLOWER ->
                    if (this is RestoreState.FollowerPending ||
                        this is RestoreState.FollowerExistenceChecked ||
                        this is RestoreState.FollowerDone
                    ) {
                        this
                    } else {
                        RestoreState.FollowerPending
                    }
            }

        is RestoreEvent.ApiKeyLoaded ->
            when (this) {
                is RestoreState.LeaderPending -> copy(apiKey = event.key)
                is RestoreState.LeaderDone -> copy(apiKey = event.key)
                else -> this
            }

        RestoreEvent.LeaderChecked -> if (this is RestoreState.LeaderPending) RestoreState.LeaderDone(apiKey) else this

        RestoreEvent.ExistenceChecked -> if (this is RestoreState.FollowerPending) RestoreState.FollowerExistenceChecked else this

        RestoreEvent.FollowerRestoreChecked -> if (this is RestoreState.FollowerExistenceChecked) RestoreState.FollowerDone else this
    }

internal val RestoreState.apiKey: String?
    get() =
        when (this) {
            is RestoreState.LeaderPending -> apiKey
            is RestoreState.LeaderDone -> apiKey
            else -> null
        }
