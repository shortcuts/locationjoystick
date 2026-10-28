package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

/**
 * Owns the wait that sends a follower back to Follow leader after the user left FOLLOWER mode by
 * hand (joystick, teleport, walk, route, roam), extracted from [MapController], mirroring
 * [FollowerCatchUpCoordinator]. Re-entry itself stays with [MockLocationService].
 */
internal class FollowerResumeCoordinator(
    private val context: Context,
    private val locationRepository: LocationRepository,
    private val groupRepository: GroupRepository,
    private val scope: CoroutineScope,
) {
    private enum class Outcome { WAIT, END, RESUME }

    private var resumeJob: Job? = null

    /**
     * Teleports, walks and route starts leave FOLLOWER mode without touching the Follow leader
     * flag, so nothing would ever re-enter it. The joystick takeover clears the flag itself and
     * calls [resumeAfterIdle] directly.
     */
    fun observeDeparture() {
        scope.launch {
            var previous = locationRepository.currentMode.value
            locationRepository.currentMode.collect { mode ->
                val left = previous == MockMode.FOLLOWER && mode != MockMode.FOLLOWER
                previous = mode
                if (left) {
                    val gs = groupRepository.groupState.first()
                    if (gs.role == GroupRole.FOLLOWER && gs.followerModeEnabled) resumeAfterIdle()
                }
            }
        }
    }

    /**
     * Resumes Follow leader once mode, position and spoofing state have been unchanged for
     * [AppConstants.SyncConstants.FOLLOWER_RESUME_IDLE_MS] in a manual mode. Any change restarts
     * the wait. Ends when spoofing stops, the device enters FOLLOWER mode, or it leaves the group.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun resumeAfterIdle() {
        resumeJob?.cancel()
        resumeJob =
            scope.launch {
                val outcome =
                    combine(
                        locationRepository.currentMode,
                        locationRepository.currentPosition,
                        locationRepository.mockLocationState,
                    ) { mode, _, state -> mode to state }
                        .mapLatest { (mode, state) ->
                            delay(AppConstants.SyncConstants.FOLLOWER_RESUME_IDLE_MS)
                            when {
                                mode == MockMode.FOLLOWER || state == MockLocationState.IDLE -> Outcome.END
                                groupRepository.groupState.first().role != GroupRole.FOLLOWER -> Outcome.END
                                mode == MockMode.JOYSTICK || mode == MockMode.TELEPORT -> Outcome.RESUME
                                else -> Outcome.WAIT
                            }
                        }.first { it != Outcome.WAIT }
                if (outcome == Outcome.RESUME) {
                    groupRepository.setFollowerModeEnabled(true)
                    context.startService(MockLocationIntentBuilder.resumeFollower(context))
                }
            }
    }
}
