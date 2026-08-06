buildscript {
    dependencies {
        classpath(libs.android.gradle.plugin)
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    base
    alias(libs.plugins.ktlint)
}

ktlint {
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

// Adding or removing a module requires updating settings.gradle.kts, ARCHITECTURE.md,
// the dependency allow-list, and these root lists together.
val leafProjects =
    listOf(
        ":app",
        ":feature:onboarding:api",
        ":feature:today:api",
        ":feature:library:api",
        ":feature:discover:api",
        ":feature:you:api",
        ":feature:media:api",
        ":feature:progress:api",
        ":feature:onboarding:impl",
        ":feature:today:impl",
        ":feature:library:impl",
        ":feature:discover:impl",
        ":feature:you:impl",
        ":feature:media:impl",
        ":feature:progress:impl",
        ":domain:model",
        ":domain:account",
        ":domain:media",
        ":domain:collection",
        ":domain:progress",
        ":domain:queue",
        ":domain:sync",
        ":domain:preferences",
        ":data:provider:api",
        ":core:common",
        ":core:testing",
        ":architecture-tests",
        ":data:account",
        ":data:media",
        ":data:collection",
        ":data:progress",
        ":data:sync",
        ":data:preferences",
        ":data:provider:anilist",
        ":data:provider:myanimelist",
        ":core:database",
        ":core:datastore",
        ":core:network",
        ":core:logging",
        ":sync:work",
        ":core:designsystem",
        ":core:ui",
        ":core:navigation",
    )

val androidLeafProjects =
    listOf(
        ":app",
        ":feature:onboarding:api",
        ":feature:today:api",
        ":feature:library:api",
        ":feature:discover:api",
        ":feature:you:api",
        ":feature:media:api",
        ":feature:progress:api",
        ":feature:onboarding:impl",
        ":feature:today:impl",
        ":feature:library:impl",
        ":feature:discover:impl",
        ":feature:you:impl",
        ":feature:media:impl",
        ":feature:progress:impl",
        ":data:account",
        ":data:media",
        ":data:collection",
        ":data:progress",
        ":data:sync",
        ":data:preferences",
        ":data:provider:anilist",
        ":data:provider:myanimelist",
        ":core:database",
        ":core:datastore",
        ":core:network",
        ":core:logging",
        ":sync:work",
        ":core:designsystem",
        ":core:ui",
        ":core:navigation",
    )

val pureJvmLeafProjects =
    listOf(
        ":domain:model",
        ":domain:account",
        ":domain:media",
        ":domain:collection",
        ":domain:progress",
        ":domain:queue",
        ":domain:sync",
        ":domain:preferences",
        ":data:provider:api",
        ":core:common",
        ":core:testing",
        ":architecture-tests",
    )

val buildLogic = gradle.includedBuild("build-logic")

tasks.register("format") {
    group = "formatting"
    description = "Formats Kotlin sources with ktlint."
    dependsOn("ktlintFormat")
    dependsOn(leafProjects.map { "$it:ktlintFormat" })
    dependsOn(buildLogic.task(":ktlintFormat"))
}

tasks.register("dependencyGuardCheck") {
    group = "verification"
    description = "Validates direct project dependencies and external dependency selectors."
    dependsOn(leafProjects.map { "$it:validateMioDependencies" })
}

tasks.register("konsistCheck") {
    group = "verification"
    description = "Runs the repository architecture checks."
    dependsOn(":architecture-tests:konsistCheck")
}

tasks.register("verify") {
    group = "verification"
    description = "Runs the complete Mio verification gate."
    dependsOn("ktlintCheck")
    dependsOn(leafProjects.map { "$it:ktlintCheck" })
    dependsOn(buildLogic.task(":check"))
    dependsOn("dependencyGuardCheck")
    dependsOn("konsistCheck")
    dependsOn(androidLeafProjects.map { "$it:lint" })
    dependsOn(leafProjects.map { "$it:test" })
    dependsOn(":app:assembleDebug")
}
