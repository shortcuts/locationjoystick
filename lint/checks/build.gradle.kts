import java.util.Properties

plugins {
    alias(libs.plugins.locationjoystick.jvm.library)
}

dependencies {
    compileOnly(libs.lintcheck.api)

    testImplementation(libs.lintcheck.core)
    testImplementation(libs.lintcheck.tests)
    testImplementation(libs.lintcheck.api)
    testImplementation(libs.junit)
}

// lint's bundled Kotlin compiler (used for Kotlin UAST parsing in tests) is built
// against Kotlin 2.2.10; Gradle's normal "highest version wins" resolution otherwise
// bumps kotlin-stdlib/kotlin-reflect to this project's 2.2.21, which silently breaks
// Kotlin UAST parsing (Java sources are unaffected; only .kt test fixtures see it).
configurations.matching { it.name.startsWith("test") }.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
        force("org.jetbrains.kotlin:kotlin-reflect:2.2.10")
    }
}

tasks.jar {
    manifest {
        attributes("Lint-Registry-v2" to "com.locationjoystick.lint.checks.LjIssueRegistry")
    }
}

// Lint's Kotlin/PSI analysis (used by lint-tests' TestLintTask) needs these
// module opens on JDK 17+ or UAST silently returns no results instead of throwing.
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.ref=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens=java.base/java.security=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED",
        "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED",
        "--add-opens=java.base/sun.security.util=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    )
}

// lint-tests aborts when ANDROID_SDK_ROOT points to a missing directory, so fall back to local.properties.
tasks.withType<Test>().configureEach {
    val sdkDir =
        rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
            Properties().apply { file.reader().use(::load) }.getProperty("sdk.dir")
        }
    if (sdkDir != null && File(sdkDir).isDirectory) {
        environment("ANDROID_SDK_ROOT", sdkDir)
        environment("ANDROID_HOME", sdkDir)
    }
}
