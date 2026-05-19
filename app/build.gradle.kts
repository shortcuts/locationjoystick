plugins {
    alias(libs.plugins.locationjoystick.android.application)
    alias(libs.plugins.locationjoystick.hilt)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.locationjoystick.app"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:location"))
    implementation(project(":core:model"))
    implementation(project(":core:overlay"))
    implementation(project(":core:routing"))

    implementation(project(":feature:onboarding:api"))
    implementation(project(":feature:onboarding:impl"))
    implementation(project(":feature:map:api"))
    implementation(project(":feature:map:impl"))
    implementation(project(":feature:joystick:api"))
    implementation(project(":feature:joystick:impl"))
    implementation(project(":feature:routes:api"))
    implementation(project(":feature:routes:impl"))
    implementation(project(":feature:favorites:api"))
    implementation(project(":feature:favorites:impl"))
    implementation(project(":feature:settings:api"))
    implementation(project(":feature:settings:impl"))
    implementation(project(":feature:widget:api"))
    implementation(project(":feature:widget:impl"))

    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}
