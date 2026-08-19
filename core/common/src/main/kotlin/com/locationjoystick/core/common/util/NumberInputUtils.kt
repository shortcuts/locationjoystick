package com.locationjoystick.core.common.util

/**
 * Parses a decimal number typed by the user, accepting both "." and "," as the decimal
 * separator regardless of the device locale's displayed separator (e.g. de/fr keyboards show
 * "," but [String.toDoubleOrNull] only ever accepts ".").
 */
fun String.toLocaleDoubleOrNull(): Double? = replace(',', '.').toDoubleOrNull()
