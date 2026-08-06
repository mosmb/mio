import org.gradle.api.tasks.PathSensitivity

plugins {
    id("mio.kotlin.library")
}

val productionKotlinSources =
    rootProject.fileTree(rootProject.projectDir) {
        include("app/src/main/kotlin/**/*.kt")
        include("feature/*/*/src/main/kotlin/**/*.kt")
        include("domain/*/src/main/kotlin/**/*.kt")
        include("data/*/src/main/kotlin/**/*.kt")
        include("data/provider/*/src/main/kotlin/**/*.kt")
        include("core/*/src/main/kotlin/**/*.kt")
        include("sync/*/src/main/kotlin/**/*.kt")
    }

val architectureFixtures = fileTree("src/test/fixtures") { include("**/*.kt") }

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.konsist)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    workingDir(rootProject.projectDir)
    systemProperty("mio.project.root", rootProject.projectDir.absolutePath)
    inputs
        .files(productionKotlinSources)
        .withPropertyName("mioProductionKotlinSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .files(architectureFixtures)
        .withPropertyName("mioArchitectureFixtures")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.register("konsistCheck") {
    group = "verification"
    description = "Runs Mio architecture rules and their violation fixtures."
    dependsOn(tasks.test)
}
