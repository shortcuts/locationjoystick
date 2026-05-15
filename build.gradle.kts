plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":app"))
    kover(project(":core:common"))
    kover(project(":core:data"))
    kover(project(":core:database"))
    kover(project(":core:datastore"))
    kover(project(":core:designsystem"))
    kover(project(":core:location"))
    kover(project(":core:model"))
    kover(project(":core:overlay"))
    kover(project(":core:routing"))
    kover(project(":core:testing"))
    kover(project(":core:ui"))
    kover(project(":feature:favorites:api"))
    kover(project(":feature:favorites:impl"))
    kover(project(":feature:joystick:api"))
    kover(project(":feature:joystick:impl"))
    kover(project(":feature:map:api"))
    kover(project(":feature:map:impl"))
    kover(project(":feature:roaming:api"))
    kover(project(":feature:roaming:impl"))
    kover(project(":feature:routes:api"))
    kover(project(":feature:routes:impl"))
    kover(project(":feature:settings:api"))
    kover(project(":feature:settings:impl"))
    kover(project(":feature:setup:api"))
    kover(project(":feature:setup:impl"))
    kover(project(":feature:widget:api"))
    kover(project(":feature:widget:impl"))
}
