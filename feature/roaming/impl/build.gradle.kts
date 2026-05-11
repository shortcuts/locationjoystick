plugins {
    alias(libs.plugins.locationjoystick.android.feature)
}

android {
    namespace = "com.locationjoystick.feature.roaming.impl"
}

dependencies {
    implementation(project(":feature:roaming:api"))
    implementation(project(":core:data"))
    implementation(project(":core:routing"))
    implementation(project(":core:ui"))

    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.compose.material.icons.extended)
}
