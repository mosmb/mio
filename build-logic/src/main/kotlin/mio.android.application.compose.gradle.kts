import com.android.build.api.dsl.ApplicationExtension
import io.morgan.mio.buildlogic.MioDependencyGuardPlugin
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

apply<MioDependencyGuardPlugin>()

ktlint {
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

extensions.configure<ApplicationExtension> {
    namespace = "io.morgan.mio"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.morgan.mio"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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
        // Versions are pinned deliberately; report newer releases without failing the build.
        informational += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
    buildFeatures {
        compose = true
    }
}

extensions.configure<KotlinAndroidProjectExtension> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    val mioLibs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
    add("implementation", platform(mioLibs.findLibrary("compose-bom").get()))
    add("implementation", mioLibs.findLibrary("compose-ui").get())
    add("implementation", mioLibs.findLibrary("compose-foundation").get())
    add("implementation", mioLibs.findLibrary("compose-animation-core").get())
    add("implementation", mioLibs.findLibrary("compose-material3").get())
    add("implementation", mioLibs.findLibrary("compose-ui-tooling-preview").get())
    add("implementation", mioLibs.findLibrary("activity-compose").get())
    add("debugImplementation", mioLibs.findLibrary("compose-ui-tooling").get())
}
