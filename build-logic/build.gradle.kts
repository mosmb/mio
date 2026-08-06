import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

ktlint {
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

tasks.named<BaseKtLintCheckTask>("runKtlintCheckOverMainSourceSet") {
    setSource(layout.projectDirectory.dir("src/main/kotlin"))
}

tasks.named<BaseKtLintCheckTask>("runKtlintFormatOverMainSourceSet") {
    setSource(layout.projectDirectory.dir("src/main/kotlin"))
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        register("mioDependencyGuard") {
            id = "mio.dependency.guard"
            implementationClass = "io.morgan.mio.buildlogic.MioDependencyGuardPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
