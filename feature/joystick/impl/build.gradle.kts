plugins {
    alias(libs.plugins.locationjoystick.android.feature)
}

android {
    namespace = "com.locationjoystick.feature.joystick.impl"
}

dependencies {
    implementation(project(":feature:joystick:api"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:overlay"))
    implementation(project(":core:location"))

    implementation(libs.hilt.navigation.compose)
    implementation(libs.bundles.lifecycle)

    testImplementation("junit:junit:4.13.2")
}
