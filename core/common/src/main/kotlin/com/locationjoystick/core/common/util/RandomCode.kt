package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants

private val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

/** Generates a short human-typeable code for local network discovery (e.g. Group Sync, QR export). */
object RandomCode {
    fun generate(length: Int = AppConstants.SyncConstants.GROUP_CODE_LENGTH): String =
        (1..length).map { CODE_CHARS.random() }.joinToString("")
}
