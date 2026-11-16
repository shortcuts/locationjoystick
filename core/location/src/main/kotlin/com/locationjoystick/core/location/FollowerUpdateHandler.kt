package com.locationjoystick.core.location

import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Applies each leader position the follower polls. Reads live service state through lambdas, never snapshots. */
internal class FollowerUpdateHandler(
    private val scope: CoroutineScope,
    private val followerCatchUp: FollowerCatchUpCoordinator,
    private val groupRepository: GroupRepository,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val serviceState: () -> MockLocationState,
    private val followLeaderTeleports: () -> Boolean,
    private val hideTeleportFeatures: () -> Boolean,
    private val currentPosition: () -> LatLng,
    private val startSpoofing: (Double, Double) -> Unit,
    private val snapToLeader: (LatLng) -> Unit,
    private val pauseForInactiveLeader: () -> Unit,
) {
    fun onUpdate(update: FollowerPositionUpdate) {
        val (lat, lon, speedMs, bearing, active) = update
        val leaderTeleported = followerCatchUp.observeTeleportSeq(update.teleportSeq)
        // An inactive leader's position is stale: keep no target so Teleport to leader
        // reports "position not yet known" instead of silently moving nowhere.
        if (active) {
            followerCatchUp.setTarget(LatLng(lat, lon), bearing, speedMs)
            groupRepository.setLeaderPosition(LatLng(lat, lon))
        }
        groupRepository.setLeaderActivity(if (active) update.activity else null)
        when (followerCatchUp.handleLeaderActiveUpdate(active, serviceState())) {
            FollowerActiveAction.BOOTSTRAP -> {
                // Leader stop, move, start again counts as a teleport; the very first bootstrap
                // (nothing reported yet) always snaps.
                val resumed = followerCatchUp.consumePausedByLeader()
                val snapNow = !resumed || shouldSnapToLeader(true, followLeaderTeleports(), hideTeleportFeatures())
                val startAt = if (snapNow) LatLng(lat, lon) else currentPosition()
                scope.launch {
                    startSpoofing(startAt.latitude, startAt.longitude)
                    if (resumed && snapNow) settingsRepository.setLastTeleportTime(System.currentTimeMillis())
                    // startSpoofing() unconditionally sets mode to TELEPORT — reassert
                    // FOLLOWER so advanceFollowerCatchUp() doesn't no-op on later ticks.
                    locationRepository.setMockMode(MockMode.FOLLOWER)
                }
            }

            FollowerActiveAction.PAUSE -> {
                followerCatchUp.clearTarget()
                groupRepository.setLeaderPosition(null)
                scope.launch { pauseForInactiveLeader() }
            }

            FollowerActiveAction.NO_OP -> {
                if (shouldSnapToLeader(leaderTeleported, followLeaderTeleports(), hideTeleportFeatures())) {
                    snapToLeader(LatLng(lat, lon))
                }
            }
        }
    }
}
