plugins {
    alias(libs.plugins.locationjoystick.android.library)
    alias(libs.plugins.locationjoystick.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.locationjoystick.core.routing"
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
}
