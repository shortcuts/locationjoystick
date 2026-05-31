plugins {
    alias(libs.plugins.locationjoystick.android.library)
    alias(libs.plugins.locationjoystick.android.library.compose)
}

android {
    namespace = "com.locationjoystick.core.map"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    api(libs.maplibre.android.sdk)
    implementation(libs.androidx.compose.ui)
    implementation(libs.bundles.lifecycle)

    testImplementation(libs.junit)
}
