plugins {
    alias(libs.plugins.locationjoystick.android.library)
}

android {
    namespace = "com.locationjoystick.core.testing"
}

dependencies {
    api(project(":core:database"))
    api(project(":core:model"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.datastore.preferences)
}
