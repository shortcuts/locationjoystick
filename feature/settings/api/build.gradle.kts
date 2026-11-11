plugins {
    alias(libs.plugins.locationjoystick.android.library)
    alias(libs.plugins.locationjoystick.android.library.compose)
}

android {
    namespace = "com.locationjoystick.feature.settings.api"
}

dependencies {
    implementation(libs.androidx.navigation.compose)
}
