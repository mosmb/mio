import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
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
    add("implementation", mioLibs.findLibrary("compose-ui-tooling-preview").get())
    add("implementation", mioLibs.findLibrary("activity-compose").get())
    add("debugImplementation", mioLibs.findLibrary("compose-ui-tooling").get())
}
