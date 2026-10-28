package com.locationjoystick.core.location

import io.mockk.mockk

/** The service builds its group controller in onCreate, which unit tests skip. */
internal fun MockLocationService.stubGroupMode() {
    MockLocationService::class.java
        .getDeclaredField("groupMode")
        .apply { isAccessible = true }
        .set(this, mockk<GroupModeController>(relaxed = true))
}
