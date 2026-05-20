import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

class LjApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
                apply("org.jetbrains.kotlinx.kover")
            }

            extensions.configure<ApplicationExtension> {
                compileSdk = 35

                val releaseKeystorePath = System.getenv("KEYSTORE_PATH")

                if (releaseKeystorePath != null) {
                    signingConfigs {
                        create("release") {
                            storeFile = File(releaseKeystorePath)
                            storePassword = System.getenv("STORE_PASSWORD")
                            keyAlias = System.getenv("KEY_ALIAS")
                            keyPassword = System.getenv("KEY_PASSWORD")
                        }
                    }
                }

                defaultConfig {
                    minSdk = 31
                    targetSdk = 35
                    buildConfigField("String", "VERSION_NAME", "\"1.0.0\"")
                }

                buildFeatures {
                    buildConfig = true
                }

                buildTypes {
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                        if (releaseKeystorePath != null) {
                            signingConfig = signingConfigs.getByName("release")
                        }
                    }
                }

                // Produce per-ABI APKs for GitHub Releases: arm64-v8a (~90 % of devices) and
                // armeabi-v7a (older 32-bit devices). Drops x86/x86_64 emulator-only slices that
                // inflate APK size significantly due to MapLibre native libraries.
                splits {
                    abi {
                        isEnable = true
                        reset()
                        include("arm64-v8a", "armeabi-v7a")
                        isUniversalApk = true // also keep a universal fallback APK
                    }
                }

                // AAB bundle config: let Play Store deliver only the language + density
                // resources each device actually needs (reduces on-device install size).
                bundle {
                    language {
                        enableSplit = true
                    }
                    density {
                        enableSplit = true
                    }
                    abi {
                        enableSplit = true
                    }
                }

                compileOptions {
                    sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                    targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
                }

                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }
            }

            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }
}
