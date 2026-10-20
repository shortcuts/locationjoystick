plugins {
    alias(libs.plugins.locationjoystick.android.library)
    alias(libs.plugins.locationjoystick.hilt)
}

android {
    namespace = "com.locationjoystick.core.data"
    sourceSets {
        getByName("main") {
            // Pack the shared changelog entries for offline use.
            assets.srcDir(rootProject.layout.projectDirectory.dir("docs/wiki/changelog"))
            // Pack the seed hot-locations list (the same file the wiki serves).
            assets.srcDir(rootProject.layout.projectDirectory.dir("docs/wiki/hot"))
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:routing"))

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.orgjson)
    testImplementation(project(":core:testing"))
}
