package com.locationjoystick.core.model

data class SpeedProfile(
    val id: String,
    val name: String,
    val speedMetersPerSecond: Double,
) {
    companion object {
        // Cannot use AppConstants — core:model is a pure JVM module with no core:common dependency.
        // Keep in sync with AppConstants.ProfileConstants.
        const val SLOW_WALK_SPEED_MPS = 0.3 // ~1.1 km/h
        const val WALK_SPEED_MPS = 10.0 / 3.6 // 10 km/h
        const val RUN_SPEED_MPS = 2.2222 // 8 km/h
        const val BIKE_SPEED_MPS = 18.5 / 3.6 // 18.5 km/h
        const val DRIVE_SPEED_MPS = 60.0 / 3.6 // 60 km/h

        fun defaultProfiles(): List<SpeedProfile> =
            listOf(
                SpeedProfile(id = "slow_walk", name = "Slow Walk", speedMetersPerSecond = SLOW_WALK_SPEED_MPS),
                SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = WALK_SPEED_MPS),
                SpeedProfile(id = "run", name = "Run", speedMetersPerSecond = RUN_SPEED_MPS),
                SpeedProfile(id = "bike", name = "Bike", speedMetersPerSecond = BIKE_SPEED_MPS),
                SpeedProfile(id = "drive", name = "Drive", speedMetersPerSecond = DRIVE_SPEED_MPS),
            )
    }
}
