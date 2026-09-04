package com.locationjoystick.core.common.util

/**
 * Parses a decimal number typed by the user, accepting both "." and "," as the decimal
 * separator regardless of the device locale's displayed separator (e.g. de/fr keyboards show
 * "," but [String.toDoubleOrNull] only ever accepts ".").
 */
fun String.toLocaleDoubleOrNull(): Double? = replace(',', '.').toDoubleOrNull()

/** True if [lat]/[lon] are non-null and within valid coordinate ranges. */
fun isValidLatLng(
    lat: Double?,
    lon: Double?,
): Boolean = lat != null && lat in -90.0..90.0 && lon != null && lon in -180.0..180.0
