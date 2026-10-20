plugins {
    alias(libs.plugins.locationjoystick.android.library)
    alias(libs.plugins.locationjoystick.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.locationjoystick.core.database"
    sourceSets {
        getByName("test") {
            assets.srcDir(projectDir.resolve("schemas"))
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.runner)
}
