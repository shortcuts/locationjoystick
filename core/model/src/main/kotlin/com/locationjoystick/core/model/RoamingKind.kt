package com.locationjoystick.core.model

/** Which roaming path planner to use for a session. Only one kind can run at a time. */
enum class RoamingKind {
    WALK_AROUND,
    PLANTING,
    ;

    companion object {
        fun parse(raw: String?): RoamingKind = raw?.let { runCatching { valueOf(it) }.getOrNull() } ?: WALK_AROUND
    }
}
