import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

group = "com.locationjoystick.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("ljJvmLibrary") {
            id = "locationjoystick.jvm.library"
            implementationClass = "LjJvmLibraryConventionPlugin"
        }
        register("ljAndroidApplication") {
            id = "locationjoystick.android.application"
            implementationClass = "LjApplicationConventionPlugin"
        }
        register("ljAndroidLibrary") {
            id = "locationjoystick.android.library"
            implementationClass = "LjLibraryConventionPlugin"
        }
        register("ljAndroidFeature") {
            id = "locationjoystick.android.feature"
            implementationClass = "LjFeatureConventionPlugin"
        }
        register("ljAndroidLibraryCompose") {
            id = "locationjoystick.android.library.compose"
            implementationClass = "LjComposeConventionPlugin"
        }
        register("ljHilt") {
            id = "locationjoystick.hilt"
            implementationClass = "LjHiltConventionPlugin"
        }
    }
}
