package com.locationjoystick.feature.map.impl

import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.RoamingDefaults

sealed interface ActiveSheet {
    data object None : ActiveSheet

    data class Favorites(
        val target: FavoriteLocation? = null,
    ) : ActiveSheet

    data object Routes : ActiveSheet

    data class Paste(
        val title: PasteSheetTitle,
        val initialText: String,
        val initialRouteName: String,
    ) : ActiveSheet

    data object PendingTap : ActiveSheet

    data class Roaming(
        val draft: RoamingDefaults,
        val minimized: Boolean = false,
    ) : ActiveSheet
}

sealed interface SheetEvent {
    data object OpenFavorites : SheetEvent

    data object OpenRoutes : SheetEvent

    data class OpenPaste(
        val title: PasteSheetTitle,
        val text: String,
        val routeName: String,
    ) : SheetEvent

    data object OpenPendingTap : SheetEvent

    data class OpenRoaming(
        val draft: RoamingDefaults,
    ) : SheetEvent

    data object CloseFavorites : SheetEvent

    data object CloseRoutes : SheetEvent

    data object ClosePaste : SheetEvent

    data object ClosePendingTap : SheetEvent

    data object CloseRoaming : SheetEvent

    data object MinimizeRoaming : SheetEvent

    data object ExpandRoaming : SheetEvent

    data class SelectFavorite(
        val favorite: FavoriteLocation,
    ) : SheetEvent

    data object DeselectFavorite : SheetEvent

    data class UpdateRoamingDraft(
        val transform: (RoamingDefaults) -> RoamingDefaults,
    ) : SheetEvent
}

/** Returns the next sheet, or null when the event is illegal in the current state. */
fun ActiveSheet.next(event: SheetEvent): ActiveSheet? =
    when (event) {
        SheetEvent.OpenFavorites -> if (this is ActiveSheet.Favorites) null else ActiveSheet.Favorites()
        SheetEvent.OpenRoutes -> if (this is ActiveSheet.Routes) null else ActiveSheet.Routes
        is SheetEvent.OpenPaste -> ActiveSheet.Paste(event.title, event.text, event.routeName)
        SheetEvent.OpenPendingTap -> if (this is ActiveSheet.PendingTap) null else ActiveSheet.PendingTap
        is SheetEvent.OpenRoaming -> if (this is ActiveSheet.Roaming) null else ActiveSheet.Roaming(event.draft)
        // A close for a sheet that is not open is a benign no-op: callers close defensively.
        SheetEvent.CloseFavorites -> if (this is ActiveSheet.Favorites) ActiveSheet.None else this
        SheetEvent.CloseRoutes -> if (this is ActiveSheet.Routes) ActiveSheet.None else this
        SheetEvent.ClosePaste -> if (this is ActiveSheet.Paste) ActiveSheet.None else this
        SheetEvent.ClosePendingTap -> if (this is ActiveSheet.PendingTap) ActiveSheet.None else this
        SheetEvent.CloseRoaming -> if (this is ActiveSheet.Roaming) ActiveSheet.None else this
        SheetEvent.MinimizeRoaming -> if (this is ActiveSheet.Roaming && !minimized) copy(minimized = true) else null
        SheetEvent.ExpandRoaming -> if (this is ActiveSheet.Roaming && minimized) copy(minimized = false) else null
        is SheetEvent.SelectFavorite -> if (this is ActiveSheet.Favorites) copy(target = event.favorite) else null
        SheetEvent.DeselectFavorite -> if (this is ActiveSheet.Favorites) copy(target = null) else null
        is SheetEvent.UpdateRoamingDraft -> if (this is ActiveSheet.Roaming) copy(draft = event.transform(draft)) else null
    }

val ActiveSheet.isModal: Boolean
    get() = this !is ActiveSheet.None && !(this is ActiveSheet.Roaming && minimized)
