package com.locationjoystick.core.model

enum class SavedItemSortMode {
    NAME_ASCENDING,
    NAME_DESCENDING,
    NEWEST_FIRST,
    OLDEST_FIRST,
}

fun <T : HasCreatedAt> List<T>.sortedBySavedItemMode(
    mode: SavedItemSortMode,
    nameOf: (T) -> String,
): List<T> =
    when (mode) {
        SavedItemSortMode.NAME_ASCENDING -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, nameOf))
        SavedItemSortMode.NAME_DESCENDING -> sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER, nameOf))
        SavedItemSortMode.NEWEST_FIRST -> sortedByDescending { it.createdAt }
        SavedItemSortMode.OLDEST_FIRST -> sortedBy { it.createdAt }
    }
