plugins {
    alias(libs.plugins.locationjoystick.android.library)
    alias(libs.plugins.locationjoystick.android.library.compose)
    alias(libs.plugins.locationjoystick.hilt)
}

android {
    namespace = "com.locationjoystick.core.designsystem"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
