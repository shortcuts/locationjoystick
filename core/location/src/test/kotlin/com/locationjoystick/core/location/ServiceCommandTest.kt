package com.locationjoystick.core.location

import android.content.Context
import android.content.Intent
import com.locationjoystick.core.common.constants.AppConstants.ServiceConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.LoopMode
import com.locationjoystick.core.model.RouteStartConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ServiceCommandTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val here = LatLng(10.0, 20.0)

    private fun action(name: String) = Intent(context, MockLocationService::class.java).apply { action = name }

    @Test
    fun start_readsCoordinates() {
        val intent = MockLocationIntentBuilder.startSpoofing(context, 1.5, 2.5)

        assertEquals(ServiceCommand.Start(1.5, 2.5), intent.toServiceCommand(here))
    }

    @Test
    fun enterFollower_readsHostPortGroup() {
        val intent =
            action(MockLocationService.ACTION_ENTER_FOLLOWER)
                .putExtra(ServiceConstants.EXTRA_FOLLOWER_HOST, "10.0.0.2")
                .putExtra(ServiceConstants.EXTRA_FOLLOWER_PORT, 8080)
                .putExtra(ServiceConstants.EXTRA_FOLLOWER_GROUP_ID, "AAA111")

        assertEquals(ServiceCommand.EnterFollower("10.0.0.2", 8080, "AAA111"), intent.toServiceCommand(here))
    }

    @Test
    fun enterFollower_missingHost_isNull() {
        val intent = action(MockLocationService.ACTION_ENTER_FOLLOWER).putExtra(ServiceConstants.EXTRA_FOLLOWER_GROUP_ID, "AAA111")

        assertNull(intent.toServiceCommand(here))
    }

    @Test
    fun enterFollower_missingGroup_isNull() {
        val intent = action(MockLocationService.ACTION_ENTER_FOLLOWER).putExtra(ServiceConstants.EXTRA_FOLLOWER_HOST, "10.0.0.2")

        assertNull(intent.toServiceCommand(here))
    }

    @Test
    fun startLeader_readsGroupId() {
        val intent = action(ServiceConstants.ACTION_START_LEADER).putExtra(ServiceConstants.EXTRA_LEADER_GROUP_ID, "AAA111")

        assertEquals(ServiceCommand.StartLeader("AAA111"), intent.toServiceCommand(here))
    }

    @Test
    fun startLeader_missingGroupId_isNull() {
        assertNull(action(ServiceConstants.ACTION_START_LEADER).toServiceCommand(here))
    }

    @Test
    fun noArgActions_mapToObjects() {
        assertEquals(ServiceCommand.Stop, action(MockLocationService.ACTION_STOP).toServiceCommand(here))
        assertEquals(ServiceCommand.ExitFollower, action(MockLocationService.ACTION_EXIT_FOLLOWER).toServiceCommand(here))
        assertEquals(ServiceCommand.ResumeFollower, action(ServiceConstants.ACTION_RESUME_FOLLOWER).toServiceCommand(here))
        assertEquals(ServiceCommand.ExitLeader, action(ServiceConstants.ACTION_EXIT_LEADER).toServiceCommand(here))
        assertEquals(ServiceCommand.ReplayJumpNext, action(MockLocationService.ACTION_ROUTE_REPLAY_JUMP_NEXT).toServiceCommand(here))
        assertEquals(ServiceCommand.FollowerTeleport, action(MockLocationService.ACTION_FOLLOWER_TELEPORT).toServiceCommand(here))
    }

    @Test
    fun nullIntent_isOsRestart() {
        assertEquals(ServiceCommand.OsRestart, (null as Intent?).toServiceCommand(here))
    }

    @Test
    fun nullAction_isOsRestart() {
        assertEquals(ServiceCommand.OsRestart, Intent().toServiceCommand(here))
    }

    @Test
    fun unknownAction_isNull() {
        assertNull(Intent("nope").toServiceCommand(here))
    }

    @Test
    fun updatePosition_readsExtras() {
        val intent = MockLocationIntentBuilder.updatePosition(context, 1.0, 2.0, 3f, 4f).putExtra(ServiceConstants.EXTRA_IS_TELEPORT, true)

        assertEquals(ServiceCommand.UpdatePosition(1.0, 2.0, 3f, 4f, true), intent.toServiceCommand(here))
    }

    @Test
    fun updatePosition_defaultsToCurrentPosition() {
        assertEquals(
            ServiceCommand.UpdatePosition(10.0, 20.0, 0f, 0f, false),
            action(MockLocationService.ACTION_UPDATE_POSITION).toServiceCommand(here),
        )
    }

    @Test
    fun ephemeralReplay_decodesWaypointsAndStops() {
        val intent = MockLocationIntentBuilder.startEphemeralReplay(context, listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)), 2.5, listOf(1))

        assertEquals(
            ServiceCommand.StartEphemeralReplay(listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)), 2.5, listOf(1)),
            intent.toServiceCommand(here),
        )
    }

    @Test
    fun ephemeralReplay_dropsBadSegmentsAndNeedsTwo() {
        val intent =
            action(MockLocationService.ACTION_ROUTE_REPLAY_START)
                .putExtra(MockLocationService.EXTRA_IS_EPHEMERAL, true)
                .putExtra(ServiceConstants.EXTRA_EPHEMERAL_WAYPOINTS, "1.0,2.0;bad;3.0")

        assertNull(intent.toServiceCommand(here))
    }

    @Test
    fun routeReplay_readsRouteSpeedConfigAndReturn() {
        val config = RouteStartConfig(loopMode = LoopMode.entries.last())
        val intent = MockLocationIntentBuilder.startRouteReplay(context, "r1", 1.4, config, LatLng(5.0, 6.0))

        assertEquals(
            ServiceCommand.StartRouteReplay("r1", 1.4, intent.toRouteStartConfig(), LatLng(5.0, 6.0)),
            intent.toServiceCommand(here),
        )
        assertEquals(config.loopMode, (intent.toServiceCommand(here) as ServiceCommand.StartRouteReplay).config.loopMode)
    }

    @Test
    fun routeReplay_missingRouteId_isNull() {
        assertNull(action(MockLocationService.ACTION_ROUTE_REPLAY_START).toServiceCommand(here))
    }

    @Test
    fun replayResume_readsSpeed() {
        val intent = MockLocationIntentBuilder.resumeRouteReplay(context, 3.0)

        assertEquals(ServiceCommand.ReplayResume(3.0), intent.toServiceCommand(here))
    }

    @Test
    fun appendWaypoint_readsPositionAndStopFlag() {
        val intent = MockLocationIntentBuilder.appendWaypoint(context, LatLng(7.0, 8.0), isStop = false)

        assertEquals(ServiceCommand.AppendWaypoint(LatLng(7.0, 8.0), false), intent.toServiceCommand(here))
        assertEquals(
            ServiceCommand.AppendWaypoint(LatLng(0.0, 0.0), true),
            action(MockLocationService.ACTION_ROUTE_APPEND_WAYPOINT).toServiceCommand(here),
        )
    }
}
