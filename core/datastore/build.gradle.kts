plugins {
    alias(libs.plugins.locationjoystick.android.library)
    alias(libs.plugins.locationjoystick.hilt)
}

android {
    namespace = "com.locationjoystick.core.datastore"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
