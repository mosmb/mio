import io.morgan.mio.buildlogic.MioDependencyGuardPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlin.jvm")
}

apply<MioDependencyGuardPlugin>()

ktlint {
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

extensions.configure<KotlinJvmExtension> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}
