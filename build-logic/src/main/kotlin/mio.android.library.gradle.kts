import com.android.build.api.dsl.LibraryExtension
import io.morgan.mio.buildlogic.MioDependencyGuardPlugin
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.util.Locale

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("com.android.library")
}

apply<MioDependencyGuardPlugin>()

ktlint {
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

val moduleNamespace =
    buildString {
        append("io.morgan.mio")
        project.path
            .split(":")
            .filter { it.isNotEmpty() }
            .forEach { append('.').append(it.lowercase(Locale.US)) }
    }

extensions.configure<LibraryExtension> {
    namespace = moduleNamespace
    compileSdk = 37
    defaultConfig {
        minSdk = 30
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        checkReleaseBuilds = true
        // Task 01 and Task 02 intentionally pin the toolchain and test BOM versions.
        informational += setOf("AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

extensions.configure<KotlinAndroidProjectExtension> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}
