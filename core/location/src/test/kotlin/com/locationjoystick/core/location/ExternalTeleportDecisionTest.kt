package com.locationjoystick.core.location

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalTeleportDecisionTest {
    private val point = LatLng(48.8584, 2.2945)

    private fun decide(
        state: MockLocationState = MockLocationState.RUNNING,
        hide: Boolean = false,
        lat: Double? = 48.8584,
        lng: Double? = 2.2945,
    ) = externalTeleportDecision(state, hide, lat, lng)

    private fun assertIgnored(d: ExternalTeleportDecision) = assertTrue("expected Ignore, got $d", d is ExternalTeleportDecision.Ignore)

    @Test fun `idle starts at the point`() = assertEquals(ExternalTeleportDecision.StartAt(point), decide(MockLocationState.IDLE))

    @Test fun `running teleports`() = assertEquals(ExternalTeleportDecision.Teleport(point), decide(MockLocationState.RUNNING))

    @Test fun `paused is ignored`() = assertIgnored(decide(MockLocationState.PAUSED))

    @Test fun `error is ignored`() = assertIgnored(decide(MockLocationState.ERROR))

    @Test fun `running with hide teleport is ignored`() = assertIgnored(decide(MockLocationState.RUNNING, hide = true))

    @Test fun `idle with hide teleport is ignored`() = assertIgnored(decide(MockLocationState.IDLE, hide = true))

    @Test fun `missing lat is ignored`() = assertIgnored(decide(lat = null))

    @Test fun `missing lng is ignored`() = assertIgnored(decide(lng = null))

    @Test fun `NaN lat is ignored`() = assertIgnored(decide(lat = Double.NaN))

    @Test fun `infinite lat is ignored`() = assertIgnored(decide(lat = Double.POSITIVE_INFINITY))

    @Test fun `lat above range is ignored`() = assertIgnored(decide(lat = 90.1))

    @Test fun `lat below range is ignored`() = assertIgnored(decide(lat = -90.1))

    @Test fun `lng above range is ignored`() = assertIgnored(decide(lng = 180.1))

    @Test fun `lng below range is ignored`() = assertIgnored(decide(lng = -180.1))

    @Test fun `bounds are accepted`() =
        assertEquals(
            ExternalTeleportDecision.Teleport(LatLng(90.0, 180.0)),
            decide(lat = 90.0, lng = 180.0),
        )
}
