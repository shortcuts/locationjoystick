package com.locationjoystick.core.common.util

/** Typed so each UI site maps a failure to a localized string instead of showing English text. */
sealed class GpxImportError : IllegalArgumentException() {
    data class TooLarge(
        val sizeMb: Long,
        val maxMb: Long,
    ) : GpxImportError()

    data class AllOversized(
        val maxPoints: Int,
    ) : GpxImportError()

    data object NoRoutes : GpxImportError()

    data object Unreadable : GpxImportError()
}
