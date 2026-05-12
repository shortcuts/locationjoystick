package com.locationjoystick.core.model

data class SpeedProfile(
    val id: String,
    val name: String,
    val speedMetersPerSecond: Double,
) {
    companion object {
        private const val WALK_SPEED_MPS = 0.556
        private const val RUN_SPEED_MPS = 2.778
        private const val BIKE_SPEED_MPS = 5.556

        fun defaultProfiles(): List<SpeedProfile> =
            listOf(
                SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = WALK_SPEED_MPS),
                SpeedProfile(id = "run", name = "Run", speedMetersPerSecond = RUN_SPEED_MPS),
                SpeedProfile(id = "bike", name = "Bike", speedMetersPerSecond = BIKE_SPEED_MPS),
            )
    }
}
