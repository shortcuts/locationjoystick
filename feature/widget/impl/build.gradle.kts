plugins {
    alias(libs.plugins.locationjoystick.android.feature)
}

android {
    namespace = "com.locationjoystick.feature.widget.impl"
    lint {
        disable += "MultipleAwaitPointerEventScopes"
    }
}

dependencies {
    implementation(project(":feature:joystick:impl"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:overlay"))
    implementation(project(":core:location"))
    implementation(project(":core:map"))
    implementation(project(":core:routing"))

    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
