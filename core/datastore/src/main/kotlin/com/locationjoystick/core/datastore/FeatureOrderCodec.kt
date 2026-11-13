package com.locationjoystick.core.datastore

import com.locationjoystick.core.model.AppFeature

inline fun <reified T : Enum<T>> String.toEnumFeature(): T? =
    enumValues<T>().firstOrNull {
        it.name.lowercase() == this
    }

/**
 * Old `WidgetFeature` names that were renamed when [AppFeature] merged it with the old
 * `MapFabFeature`. Keeps existing users' widget toggle choices intact across the upgrade.
 */
private val legacyAppFeatureAliases =
    mapOf(
        "routes_floating" to AppFeature.ROUTES,
        "favorites_floating" to AppFeature.FAVORITES,
    )

fun String.toAppFeature(): AppFeature? = legacyAppFeatureAliases[this] ?: toEnumFeature<AppFeature>()

fun List<AppFeature>.withMissingEntriesAppended(): List<AppFeature> = this + (AppFeature.entries - this.toSet())

fun parseFeatureOrder(raw: String?): List<AppFeature> {
    if (raw.isNullOrBlank()) return AppFeature.DEFAULT_ORDER
    return raw.split(",").mapNotNull { it.toAppFeature() }.withMissingEntriesAppended()
}

/**
 * Map FAB names that were in [AppFeature.DEFAULT_MAP_ENABLED] before
 * [AppFeature.PASTE_COORDINATES]. Used when `map_fab_seen_defaults` is absent so an
 * in-place upgrade gets new default-on FABs without re-enabling buttons the user
 * already turned off.
 */
val LEGACY_MAP_FAB_SEEN_DEFAULTS: Set<String> = setOf("favorites", "routes", "roaming", "search")

/**
 * Widget items in [AppFeature.DEFAULT_WIDGET_ENABLED] before [AppFeature.PASTE_COORDINATES]
 * and before [AppFeature.ROAMING] was eligible on the widget (it was map-only until 0.20.5).
 * Used when `widget_seen_defaults` is absent so an in-place upgrade gets those default-on
 * buttons without re-enabling buttons the user already turned off.
 */
val LEGACY_WIDGET_SEEN_DEFAULTS: Set<String> =
    setOf("map_floating", "joystick_toggle", "joystick_lock", "routes", "favorites", "speed_cycle")

/**
 * Widget defaults after paste landed and before roaming was added to the widget.
 * An install that saved Settings in that window has this in `widget_seen_defaults`;
 * roaming is still unseen, so [mergeNewDefaultWidgetFeatures] turns it on once.
 * If `widget_seen_defaults` already contains `roaming`, the user ran a build where
 * the widget button was a default and then turned it off — leave it off.
 */
val WIDGET_PRE_ROAMING_SEEN_DEFAULTS: Set<String> = LEGACY_WIDGET_SEEN_DEFAULTS + "paste_coordinates"

/**
 * Union any [currentDefaults] that were introduced after [seenDefaults] into [stored].
 * A null [seenDefaults] means this install has never recorded the default set (pre-migration
 * DataStore), so [legacySeenDefaults] stands in.
 */
fun mergeNewDefaultFeatures(
    stored: Set<String>,
    seenDefaults: Set<String>?,
    currentDefaults: Set<String>,
    legacySeenDefaults: Set<String>,
): Set<String> = stored + (currentDefaults - (seenDefaults ?: legacySeenDefaults))

fun mergeNewDefaultMapFeatures(
    stored: Set<String>,
    seenDefaults: Set<String>?,
    currentDefaults: Set<String> = AppFeature.DEFAULT_MAP_ENABLED.map { it.name.lowercase() }.toSet(),
    legacySeenDefaults: Set<String> = LEGACY_MAP_FAB_SEEN_DEFAULTS,
): Set<String> = mergeNewDefaultFeatures(stored, seenDefaults, currentDefaults, legacySeenDefaults)

fun mergeNewDefaultWidgetFeatures(
    stored: Set<String>,
    seenDefaults: Set<String>?,
    currentDefaults: Set<String> = AppFeature.DEFAULT_WIDGET_ENABLED.map { it.name.lowercase() }.toSet(),
    legacySeenDefaults: Set<String> = LEGACY_WIDGET_SEEN_DEFAULTS,
): Set<String> = mergeNewDefaultFeatures(stored, seenDefaults, currentDefaults, legacySeenDefaults)

fun List<AppFeature>.serializeFeatureOrder(): String = joinToString(",") { it.name.lowercase() }
