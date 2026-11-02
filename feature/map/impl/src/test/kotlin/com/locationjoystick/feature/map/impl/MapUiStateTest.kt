package com.locationjoystick.feature.map.impl

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.RoamingDefaults
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapUiStateTest {
    private val position = LatLng(latitude = 1.0, longitude = 2.0)

    @Test
    fun `hasModalSheetOpen is false by default`() {
        assertFalse(MapUiState().hasModalSheetOpen)
    }

    @Test
    fun `hasModalSheetOpen is true while the routes sheet is open`() {
        assertTrue(MapUiState(showRoutesSheet = true).hasModalSheetOpen)
    }

    @Test
    fun `hasModalSheetOpen is true while the favorites sheet is open`() {
        assertTrue(MapUiState(showFavoritesSheet = true).hasModalSheetOpen)
    }

    @Test
    fun `hasModalSheetOpen is true while the paste coordinates sheet is open`() {
        assertTrue(MapUiState(showPasteCoordinatesSheet = true).hasModalSheetOpen)
    }

    @Test
    fun `hasModalSheetOpen is true while the tap sheet is open with a position`() {
        assertTrue(MapUiState(isPendingTapSheetOpen = true, pendingTapPosition = position).hasModalSheetOpen)
    }

    @Test
    fun `hasModalSheetOpen is false when the tap sheet is open without a position`() {
        assertFalse(MapUiState(isPendingTapSheetOpen = true).hasModalSheetOpen)
    }

    @Test
    fun `hasModalSheetOpen is true while the roaming sheet is open and expanded`() {
        val state = MapUiState(showRoamingSheet = true, roamingDraft = RoamingDefaults())
        assertTrue(state.hasModalSheetOpen)
    }

    @Test
    fun `hasModalSheetOpen is false when the roaming sheet is minimized`() {
        val state =
            MapUiState(
                showRoamingSheet = true,
                roamingDraft = RoamingDefaults(),
                isRoamingSheetMinimized = true,
            )
        assertFalse(state.hasModalSheetOpen)
    }

    @Test
    fun `hasModalSheetOpen is false when the roaming sheet has no draft`() {
        assertFalse(MapUiState(showRoamingSheet = true).hasModalSheetOpen)
    }

    @Test
    fun `hasModalSheetOpen is false for the capture coordinates sheet`() {
        assertFalse(MapUiState(showCaptureCoordinatesSheet = true).hasModalSheetOpen)
    }

    @Test
    fun `showMapHint is false while idle when teleport is enabled`() {
        val state = MapUiState(mockLocationState = MockLocationState.IDLE, hideTeleportFeatures = false)

        assertFalse(state.showMapHint)
    }

    @Test
    fun `showMapHint is true while running when teleport is enabled`() {
        val state = MapUiState(mockLocationState = MockLocationState.RUNNING, hideTeleportFeatures = false)

        assertTrue(state.showMapHint)
    }

    @Test
    fun `showMapHint is true while idle when teleport is hidden`() {
        val state = MapUiState(mockLocationState = MockLocationState.IDLE, hideTeleportFeatures = true)

        assertTrue(state.showMapHint)
    }
}
