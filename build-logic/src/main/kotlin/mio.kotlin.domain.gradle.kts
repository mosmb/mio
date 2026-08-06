import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension

plugins {
    id("mio.kotlin.library")
}

extensions.configure<KotlinJvmExtension> {
    explicitApi()
}
