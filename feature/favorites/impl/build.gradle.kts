plugins {
    alias(libs.plugins.locationjoystick.android.feature)
}

android {
    namespace = "com.locationjoystick.feature.favorites.impl"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":feature:favorites:api"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:location"))
    implementation(project(":core:overlay"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:map"))

    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.compose.material.icons.extended)
    // maplibre-android-sdk is available transitively via :core:map (api dependency)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(project(":core:testing"))
}
