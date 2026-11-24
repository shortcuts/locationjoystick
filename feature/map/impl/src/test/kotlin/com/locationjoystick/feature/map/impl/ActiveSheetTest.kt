package com.locationjoystick.feature.map.impl

import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSheetTest {
    private val draft = RoamingDefaults()
    private val favorite = FavoriteLocation(id = "f", name = "f", position = LatLng(1.0, 2.0))
    private val paste = ActiveSheet.Paste(PasteSheetTitle.GPX, "t", "r")
    private val openPaste = SheetEvent.OpenPaste(PasteSheetTitle.GPX, "t", "r")
    private val others =
        listOf(
            ActiveSheet.None,
            ActiveSheet.Favorites(),
            ActiveSheet.Routes,
            paste,
            ActiveSheet.PendingTap,
            ActiveSheet.Roaming(draft),
        )

    @Test
    fun `each open event from None opens its sheet`() {
        assertEquals(ActiveSheet.Favorites(), ActiveSheet.None.next(SheetEvent.OpenFavorites))
        assertEquals(ActiveSheet.Routes, ActiveSheet.None.next(SheetEvent.OpenRoutes))
        assertEquals(paste, ActiveSheet.None.next(openPaste))
        assertEquals(ActiveSheet.PendingTap, ActiveSheet.None.next(SheetEvent.OpenPendingTap))
        assertEquals(ActiveSheet.Roaming(draft), ActiveSheet.None.next(SheetEvent.OpenRoaming(draft)))
    }

    @Test
    fun `opening a sheet replaces any other sheet`() {
        others.filterNot { it is ActiveSheet.Favorites }.forEach {
            assertEquals(ActiveSheet.Favorites(), it.next(SheetEvent.OpenFavorites))
        }
        others.filterNot { it is ActiveSheet.Routes }.forEach {
            assertEquals(ActiveSheet.Routes, it.next(SheetEvent.OpenRoutes))
        }
        others.filterNot { it is ActiveSheet.PendingTap }.forEach {
            assertEquals(ActiveSheet.PendingTap, it.next(SheetEvent.OpenPendingTap))
        }
        others.filterNot { it is ActiveSheet.Roaming }.forEach {
            assertEquals(ActiveSheet.Roaming(draft), it.next(SheetEvent.OpenRoaming(draft)))
        }
    }

    @Test
    fun `opening paste is legal from every state including paste`() {
        others.forEach { assertEquals(paste, it.next(openPaste)) }
    }

    @Test
    fun `reopening the same sheet type is rejected`() {
        assertNull(ActiveSheet.Favorites(favorite).next(SheetEvent.OpenFavorites))
        assertNull(ActiveSheet.Routes.next(SheetEvent.OpenRoutes))
        assertNull(ActiveSheet.PendingTap.next(SheetEvent.OpenPendingTap))
        assertNull(ActiveSheet.Roaming(draft).next(SheetEvent.OpenRoaming(draft)))
    }

    @Test
    fun `each close event closes its own sheet`() {
        assertEquals(ActiveSheet.None, ActiveSheet.Favorites(favorite).next(SheetEvent.CloseFavorites))
        assertEquals(ActiveSheet.None, ActiveSheet.Routes.next(SheetEvent.CloseRoutes))
        assertEquals(ActiveSheet.None, paste.next(SheetEvent.ClosePaste))
        assertEquals(ActiveSheet.None, ActiveSheet.PendingTap.next(SheetEvent.ClosePendingTap))
        assertEquals(ActiveSheet.None, ActiveSheet.Roaming(draft, minimized = true).next(SheetEvent.CloseRoaming))
    }

    @Test
    fun `a close event for another sheet leaves the state unchanged`() {
        assertEquals(ActiveSheet.PendingTap, ActiveSheet.PendingTap.next(SheetEvent.ClosePaste))
        assertEquals(ActiveSheet.Routes, ActiveSheet.Routes.next(SheetEvent.CloseFavorites))
        assertEquals(paste, paste.next(SheetEvent.CloseRoutes))
        assertEquals(ActiveSheet.None, ActiveSheet.None.next(SheetEvent.CloseRoaming))
        assertEquals(ActiveSheet.Roaming(draft), ActiveSheet.Roaming(draft).next(SheetEvent.ClosePendingTap))
    }

    @Test
    fun `minimize then expand roaming`() {
        val minimized = ActiveSheet.Roaming(draft).next(SheetEvent.MinimizeRoaming)
        assertEquals(ActiveSheet.Roaming(draft, minimized = true), minimized)
        assertEquals(ActiveSheet.Roaming(draft), minimized?.next(SheetEvent.ExpandRoaming))
    }

    @Test
    fun `minimize and expand are rejected in the wrong state`() {
        assertNull(ActiveSheet.Roaming(draft, minimized = true).next(SheetEvent.MinimizeRoaming))
        assertNull(ActiveSheet.Routes.next(SheetEvent.MinimizeRoaming))
        assertNull(ActiveSheet.Roaming(draft).next(SheetEvent.ExpandRoaming))
        assertNull(ActiveSheet.None.next(SheetEvent.ExpandRoaming))
    }

    @Test
    fun `select then deselect favorite`() {
        val selected = ActiveSheet.Favorites().next(SheetEvent.SelectFavorite(favorite))
        assertEquals(ActiveSheet.Favorites(favorite), selected)
        assertEquals(ActiveSheet.Favorites(), selected?.next(SheetEvent.DeselectFavorite))
    }

    @Test
    fun `select and deselect favorite are rejected outside favorites`() {
        assertNull(ActiveSheet.None.next(SheetEvent.SelectFavorite(favorite)))
        assertNull(ActiveSheet.Routes.next(SheetEvent.DeselectFavorite))
    }

    @Test
    fun `update roaming draft applies the transform`() {
        val next = ActiveSheet.Roaming(draft).next(SheetEvent.UpdateRoamingDraft { it.copy(radiusMeters = 7.0) })
        assertEquals(ActiveSheet.Roaming(draft.copy(radiusMeters = 7.0)), next)
    }

    @Test
    fun `update roaming draft is rejected outside roaming`() {
        assertNull(ActiveSheet.None.next(SheetEvent.UpdateRoamingDraft { it }))
        assertNull(paste.next(SheetEvent.UpdateRoamingDraft { it }))
    }

    @Test
    fun `isModal is false only for none and minimized roaming`() {
        assertFalse(ActiveSheet.None.isModal)
        assertFalse(ActiveSheet.Roaming(draft, minimized = true).isModal)
        assertTrue(ActiveSheet.Roaming(draft).isModal)
        assertTrue(ActiveSheet.PendingTap.isModal)
        assertTrue(paste.isModal)
    }
}
