package com.locationjoystick.core.model

/** A surface a configurable feature button can be shown on. */
enum class FeatureSurface {
    WIDGET,
    MAP,
}

/**
 * A user-configurable quick-access feature. Each feature declares which [FeatureSurface]s it is
 * eligible to appear on; a feature can be toggled on/off independently per surface, but shares one
 * global display order ([AppFeature.entries] order = the default) across all surfaces so the
 * floating widget panel and the map FAB column stay consistent by default.
 */
enum class AppFeature(
    val surfaces: Set<FeatureSurface>,
) {
    MAP_FLOATING(setOf(FeatureSurface.WIDGET)),
    JOYSTICK_TOGGLE(setOf(FeatureSurface.WIDGET)),
    JOYSTICK_LOCK(setOf(FeatureSurface.WIDGET)),
    FAVORITES(setOf(FeatureSurface.WIDGET, FeatureSurface.MAP)),
    ROUTES(setOf(FeatureSurface.WIDGET, FeatureSurface.MAP)),
    ROAMING(setOf(FeatureSurface.MAP)),
    SEARCH(setOf(FeatureSurface.MAP)),
    SPEED_CYCLE(setOf(FeatureSurface.WIDGET)),
    ;

    companion object {
        val DEFAULT_ORDER: List<AppFeature> = entries.toList()
        val DEFAULT_MAP_ORDER: List<AppFeature> = DEFAULT_ORDER.filter { FeatureSurface.MAP in it.surfaces }
        val DEFAULT_WIDGET_ENABLED: Set<AppFeature> =
            setOf(MAP_FLOATING, JOYSTICK_TOGGLE, JOYSTICK_LOCK, ROUTES, FAVORITES, SPEED_CYCLE)
        val DEFAULT_MAP_ENABLED: Set<AppFeature> = setOf(FAVORITES, ROUTES, ROAMING, SEARCH)
    }
}
