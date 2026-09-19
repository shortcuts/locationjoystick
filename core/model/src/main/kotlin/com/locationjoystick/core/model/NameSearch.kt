package com.locationjoystick.core.model

/**
 * Case-insensitive substring match used by favorites and routes list search.
 * Blank [query] matches everything. [extra] is an optional second field (e.g. favorite category).
 */
fun matchesNameSearch(
    name: String,
    query: String,
    extra: String? = null,
): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (name.contains(q, ignoreCase = true)) return true
    return extra?.contains(q, ignoreCase = true) == true
}

fun FavoriteLocation.matchesSearch(query: String): Boolean = matchesNameSearch(name, query, extra = category)

fun Route.matchesSearch(query: String): Boolean = matchesNameSearch(name, query)
