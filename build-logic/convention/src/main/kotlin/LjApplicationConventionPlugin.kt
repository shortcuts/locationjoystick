import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

class LjApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlinx.kover")
            }

            // Debug builds carry the commit so tooling can tell a stale install. Lazy: git must not run at configuration time.
            val gitSuffix =
                providers
                    .exec {
                        commandLine("git", "rev-parse", "--short=8", "HEAD")
                        isIgnoreExitValue = true
                    }.standardOutput.asText
                    .zip(
                        providers
                            .exec {
                                commandLine("git", "status", "--porcelain", "--untracked-files=no")
                                isIgnoreExitValue = true
                            }.standardOutput.asText,
                    ) { head, status -> head.trim().ifEmpty { "nogit" } + if (status.isBlank()) "" else "-dirty" }
            extensions.configure<ApplicationExtension> {
                compileSdk = 37

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
                    minSdk = 28
                    targetSdk = 36
                    val versionName = "1.2.1" // x-release-please-version
                    val (maj, min, pat) = versionName.substringBefore("-").split(".").map { it.toInt() }
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

                lint {
                    error += "HardcodedComposeString"
                    // The project logs with android.util.Log, not Timber (docs/code-style.md).
                    disable += "LogNotTimber"
                }
            }

            extensions.configure<ApplicationAndroidComponentsExtension> {
                onVariants(selector().withBuildType("debug")) { variant ->
                    variant.outputs.forEach { output ->
                        // Read the plain value first: zipping the property with itself is a circular reference.
                        val base = output.versionName.get()
                        output.versionName.set(gitSuffix.map { suffix -> "$base-$suffix" })
                    }
                }
            }

            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }

            dependencies {
                add("lintChecks", project(":lint:checks"))
            }
        }
    }
}
