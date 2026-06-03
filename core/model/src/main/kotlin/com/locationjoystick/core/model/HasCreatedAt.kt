package com.locationjoystick.core.model

interface HasCreatedAt {
    val createdAt: Long
}

fun <T : HasCreatedAt> List<T>.sortedByAge(newestFirst: Boolean): List<T> =
    if (newestFirst) sortedByDescending { it.createdAt } else sortedBy { it.createdAt }
