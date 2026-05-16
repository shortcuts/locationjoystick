plugins {
    alias(libs.plugins.locationjoystick.android.feature)
}

android {
    namespace = "com.locationjoystick.feature.favorites.impl"
}

dependencies {
    implementation(project(":feature:favorites:api"))
    implementation(project(":core:data"))
    implementation(project(":core:location"))
    implementation(project(":core:overlay"))
    implementation(project(":core:designsystem"))

    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.maplibre.android.sdk)
}
