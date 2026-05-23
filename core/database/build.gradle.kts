plugins {
    alias(libs.plugins.locationjoystick.android.library)
    alias(libs.plugins.locationjoystick.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.locationjoystick.core.database"
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
}
