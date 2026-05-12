plugins {
    alias(libs.plugins.locationjoystick.android.feature)
}

android {
    namespace = "com.locationjoystick.feature.widget.impl"
}

dependencies {
    implementation(project(":feature:widget:api"))
    implementation(project(":feature:joystick:impl"))
    implementation(project(":core:data"))
    implementation(project(":core:overlay"))
    implementation(project(":core:location"))

    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.compose.material.icons.extended)
}
