package com.locationjoystick.core.designsystem.component

import java.io.Serializable

/**
 * One section of a grouped list. [name] null = ungrouped. [isUserGroup] is true only for
 * groups the user made, so only those offer rename/delete (hot country groups are read-only).
 */
data class ListGroup<T>(
    val name: String?,
    val items: List<T>,
    val isUserGroup: Boolean,
)

/**
 * Splits [items] into display sections: user groups first so local favorites sit above the
 * hot-location country lists, then ungrouped, then hot country groups (A–Z). Ungrouped items go
 * before the country groups so they cannot read as part of the last country header. User groups
 * keep the order in which their first item appears in [items], so the active sort (or a drag-set
 * manual order) decides group order.
 */
fun <T> groupForDisplay(
    items: List<T>,
    categoryOf: (T) -> String?,
    isHot: (T) -> Boolean,
): List<ListGroup<T>> {
    val (hot, user) = items.partition(isHot)
    val userByName = user.groupBy { categoryOf(it) }
    val hotByName = hot.groupBy { categoryOf(it) }
    val ungrouped = userByName[null].orEmpty() + hotByName[null].orEmpty()
    // A user group may share a hot country's name; they stay separate sections.
    return buildList {
        userByName.keys
            .filterNotNull()
            .forEach { add(ListGroup(it, userByName.getValue(it), isUserGroup = true)) }
        if (ungrouped.isNotEmpty()) add(ListGroup(null, ungrouped, isUserGroup = false))
        hotByName.keys
            .filterNotNull()
            .sorted()
            .forEach { add(ListGroup(it, hotByName.getValue(it), isUserGroup = false)) }
    }
}

/**
 * Lazy-list key of a section header. A [Serializable] data class so it survives state saving;
 * rows keep their own string key.
 */
data class GroupHeaderKey(
    val isUserGroup: Boolean,
    val name: String?,
) : Serializable

internal fun <T> ListGroup<T>.headerKey() = GroupHeaderKey(isUserGroup, name)

/**
 * The sections after dragging row or user-group header [from] onto [to]; null when the pair is not
 * a valid move. Rows move within their own section, user-group headers among user groups.
 */
internal fun <T> moveKey(
    sections: List<ListGroup<T>>,
    itemKey: (T) -> String,
    from: Any,
    to: Any,
): List<ListGroup<T>>? {
    if (from is GroupHeaderKey) {
        if (to !is GroupHeaderKey || !from.isUserGroup || !to.isUserGroup) return null
        val user = sections.filter { it.isUserGroup }
        val fi = user.indexOfFirst { it.name == from.name }
        val ti = user.indexOfFirst { it.name == to.name }
        if (fi < 0 || ti < 0) return null
        return user.toMutableList().apply { add(ti, removeAt(fi)) } + sections.filter { !it.isUserGroup }
    }
    val section = sections.firstOrNull { g -> g.items.any { itemKey(it) == from } } ?: return null
    val fi = section.items.indexOfFirst { itemKey(it) == from }
    val ti = section.items.indexOfFirst { itemKey(it) == to }
    if (ti < 0) return null
    val moved = section.items.toMutableList().apply { add(ti, removeAt(fi)) }
    return sections.map { if (it === section) it.copy(items = moved) else it }
}

/**
 * Every item key in display order after moving the row or user-group header [key] by [delta]
 * places inside its own section list; null when the move leaves the list.
 */
internal fun <T> movedKeys(
    sections: List<ListGroup<T>>,
    itemKey: (T) -> String,
    key: Any,
    delta: Int,
): List<String>? {
    val siblings: List<Any> =
        if (key is GroupHeaderKey) {
            sections.filter { it.isUserGroup }.map { it.headerKey() }
        } else {
            sections.firstOrNull { g -> g.items.any { itemKey(it) == key } }?.items?.map(itemKey) ?: return null
        }
    val target = siblings.getOrNull(siblings.indexOf(key) + delta) ?: return null
    return moveKey(sections, itemKey, key, target)?.flatMap { g -> g.items.map(itemKey) }
}
