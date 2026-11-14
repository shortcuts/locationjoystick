package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.TapToWalkMove
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapToWalkStateTest {
    private val overlay = mockk<TapToWalkOverlay>()
    private val other = mockk<TapToWalkOverlay>()
    private val tap = GroundTap(LatLng(40.0, -74.0), 1.0, 2.0)
    private val target = LatLng(40.1, -74.1)
    private val shown = TapToWalkState.Shown(overlay)
    private val walking = TapToWalkState.Walking(overlay, tap, target)
    private val decision = TapDecision.Choose(target, listOf(TapToWalkMove.WALK, TapToWalkMove.TELEPORT))
    private val choosing = TapToWalkState.Choosing(overlay, tap, decision)
    private val offer = TapToWalkEvent.ChoiceOffered(tap, decision)

    @Test
    fun hidden_open_isShown() = assertEquals(shown, TapToWalkState.Hidden.next(TapToWalkEvent.OverlayOpened(overlay)))

    @Test
    fun walkingWithoutOverlay_open_keepsTap() =
        assertEquals(walking, TapToWalkState.Walking(null, tap, target).next(TapToWalkEvent.OverlayOpened(overlay)))

    @Test
    fun shown_open_isRejected() = assertNull(shown.next(TapToWalkEvent.OverlayOpened(other)))

    @Test
    fun walkingWithOverlay_open_isRejected() = assertNull(walking.next(TapToWalkEvent.OverlayOpened(other)))

    @Test
    fun shown_close_isHidden() = assertEquals(TapToWalkState.Hidden, shown.next(TapToWalkEvent.OverlayClosed))

    @Test
    fun walking_close_keepsTapWithoutOverlay() =
        assertEquals(TapToWalkState.Walking(null, tap, target), walking.next(TapToWalkEvent.OverlayClosed))

    @Test
    fun hidden_close_isRejected() = assertNull(TapToWalkState.Hidden.next(TapToWalkEvent.OverlayClosed))

    @Test
    fun shown_aim_isWalking() = assertEquals(walking, shown.next(TapToWalkEvent.Aimed(tap, target)))

    @Test
    fun hidden_aim_isWalkingWithoutOverlay() =
        assertEquals(TapToWalkState.Walking(null, tap, target), TapToWalkState.Hidden.next(TapToWalkEvent.Aimed(tap, target)))

    @Test
    fun walking_aim_swapsTarget() {
        val next = LatLng(41.0, -74.0)
        assertEquals(TapToWalkState.Walking(overlay, tap, next), walking.next(TapToWalkEvent.Aimed(tap, next)))
    }

    @Test
    fun walking_walkEnded_returnsToShown() = assertEquals(shown, walking.next(TapToWalkEvent.WalkEnded))

    @Test
    fun walkingWithoutOverlay_walkEnded_isHidden() =
        assertEquals(TapToWalkState.Hidden, TapToWalkState.Walking(null, tap, target).next(TapToWalkEvent.WalkEnded))

    @Test
    fun shown_walkEnded_isRejected() = assertNull(shown.next(TapToWalkEvent.WalkEnded))

    @Test
    fun isActive_followsOverlay() {
        assertFalse(TapToWalkState.Hidden.isActive)
        assertTrue(shown.isActive)
        assertTrue(walking.isActive)
        assertFalse(TapToWalkState.Walking(null, tap, target).isActive)
    }

    @Test
    fun shown_offer_isChoosing() = assertEquals(choosing, shown.next(offer))

    @Test
    fun walkingWithOverlay_offer_keepsTheWalk() =
        assertEquals(TapToWalkState.Choosing(overlay, tap, decision, walking = walking), walking.next(offer))

    @Test
    fun hidden_offer_isRejected() = assertNull(TapToWalkState.Hidden.next(offer))

    @Test
    fun walkingWithoutOverlay_offer_isRejected() = assertNull(TapToWalkState.Walking(null, tap, target).next(offer))

    @Test
    fun choosing_offer_isRejected() = assertNull(choosing.next(offer))

    @Test
    fun choosing_close_isHidden() = assertEquals(TapToWalkState.Hidden, choosing.next(TapToWalkEvent.OverlayClosed))

    @Test
    fun choosingOverWalk_close_restoresTheWalkWithoutOverlay() =
        assertEquals(
            TapToWalkState.Walking(null, tap, target),
            TapToWalkState.Choosing(overlay, tap, decision, walking = walking).next(TapToWalkEvent.OverlayClosed),
        )

    @Test
    fun choosing_aim_isWalkingWithOverlay() {
        val next = LatLng(41.0, -74.0)
        assertEquals(
            TapToWalkState.Walking(overlay, tap, next, TapToWalkMove.VIA_ROADS),
            choosing.next(TapToWalkEvent.Aimed(tap, next, TapToWalkMove.VIA_ROADS)),
        )
    }

    @Test
    fun choosing_open_isRejected() = assertNull(choosing.next(TapToWalkEvent.OverlayOpened(other)))

    @Test
    fun choosing_walkEnded_isRejected() = assertNull(choosing.next(TapToWalkEvent.WalkEnded))

    @Test
    fun choosing_isActive() = assertTrue(choosing.isActive)
}
