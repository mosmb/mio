import com.android.build.api.dsl.LibraryExtension
import java.util.Locale
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.library")
}

val moduleNamespace = buildString {
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
}

extensions.configure<KotlinAndroidProjectExtension> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}
