package com.locationjoystick.core.model

/** Guided onboarding path chosen by the user (docs/domain-models.md). Stored by name. */
enum class GuidedPath {
    DEFAULT,
    POKEMON_GO,
    PIKMIN_BLOOM,
    ;

    companion object {
        /** Lenient parse for persisted/imported values — unknown names fall back to [DEFAULT]. */
        fun fromName(name: String?): GuidedPath = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
