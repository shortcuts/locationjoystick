package com.locationjoystick.core.model

data class SpeedProfile(
    val id: String,
    val name: String,
    val speedMetersPerSecond: Double,
) {
    companion object {
        // Cannot use AppConstants — core:model is a pure JVM module with no core:common dependency.
        // Keep in sync with AppConstants.ProfileConstants.
        const val WALK_SPEED_MPS = 0.5556 // 2 km/h
        const val RUN_SPEED_MPS = 2.2222 // 8 km/h
        const val BIKE_SPEED_MPS = 4.1667 // 15 km/h

        fun defaultProfiles(): List<SpeedProfile> =
            listOf(
                SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = WALK_SPEED_MPS),
                SpeedProfile(id = "run", name = "Run", speedMetersPerSecond = RUN_SPEED_MPS),
                SpeedProfile(id = "bike", name = "Bike", speedMetersPerSecond = BIKE_SPEED_MPS),
            )
    }
}
