plugins {
    alias(libs.plugins.locationjoystick.android.feature)
}

android {
    namespace = "com.locationjoystick.feature.map.impl"
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":feature:map:api"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:location"))
    implementation(project(":core:designsystem"))

    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.maplibre.android.sdk)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
