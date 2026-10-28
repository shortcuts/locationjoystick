package com.locationjoystick.core.datastore

// Unit separator: cannot appear in the uuid / hot ids that make up the list.
private const val SEPARATOR = '\u001F'

internal fun serializeManualOrder(ids: List<String>): String = ids.joinToString(SEPARATOR.toString())

internal fun deserializeManualOrder(raw: String?): List<String> = raw?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
