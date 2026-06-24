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
                compileSdk = 36

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
                    targetSdk = 36
                    val versionName = "0.12.0" // x-release-please-version
                    val (maj, min, pat) = versionName.split(".").map { it.toInt() }
                    this.versionName = versionName
                    versionCode = maj * 10000 + min * 100 + pat
                    buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
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

                // Produce a single universal APK for GitHub Releases.
                splits {
                    abi {
                        isEnable = false
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
