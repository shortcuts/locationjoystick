plugins {
    alias(libs.plugins.locationjoystick.android.feature)
}

android {
    namespace = "com.locationjoystick.feature.onboarding.impl"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":feature:onboarding:api"))
    implementation(project(":core:data"))
    implementation(project(":core:location"))

    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.compose.material.icons.extended)
}
