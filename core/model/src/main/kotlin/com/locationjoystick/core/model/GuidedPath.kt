package com.locationjoystick.core.model

/** Guided onboarding path chosen by the user (docs/domain-models.md). Stored by name. */
enum class GuidedPath {
    DEFAULT,
    POKEMON_GO,
    PIKMIN_BLOOM,
    ;

    /** Planting is a Pikmin Bloom feature: hidden on Pokémon Go. UI visibility only. */
    val showsPlanting: Boolean get() = this != POKEMON_GO

    /** Tap to Walk is a Pokémon Go feature: hidden on Pikmin Bloom. */
    val showsTapToWalk: Boolean get() = this != PIKMIN_BLOOM

    /** Reconnect Accessory is a Pokémon Go feature: hidden on Pikmin Bloom. */
    val showsReconnectAccessory: Boolean get() = this != PIKMIN_BLOOM

    companion object {
        /** Lenient parse for persisted/imported values — unknown names fall back to [DEFAULT]. */
        fun fromName(name: String?): GuidedPath = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
