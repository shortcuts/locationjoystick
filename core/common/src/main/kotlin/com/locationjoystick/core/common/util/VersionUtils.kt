package com.locationjoystick.core.common.util

/**
 * True when [latest] is a strictly newer dotted version than [current]. Both are compared after
 * stripping a leading "v" (GitHub tag format) and any "-" pre-release suffix. Missing segments
 * count as 0; a non-numeric segment makes the comparison report false.
 */
fun isNewerVersion(
    latest: String,
    current: String,
): Boolean {
    val latestParts = versionParts(latest) ?: return false
    val currentParts = versionParts(current) ?: return false
    for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
        val l = latestParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (l != c) return l > c
    }
    return false
}

private fun versionParts(version: String): List<Int>? =
    version
        .removePrefix("v")
        .substringBefore("-")
        .split(".")
        .map { it.toIntOrNull() ?: return null }
