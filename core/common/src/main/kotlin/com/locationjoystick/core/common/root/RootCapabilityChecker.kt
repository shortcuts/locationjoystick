package com.locationjoystick.core.common.root

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootCapabilityChecker @Inject constructor() {
    fun isRooted(): Boolean = SU_PATHS.any { File(it).exists() }

    private companion object {
        val SU_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/adb/magisk",
            "/data/adb/ksu",
        )
    }
}
