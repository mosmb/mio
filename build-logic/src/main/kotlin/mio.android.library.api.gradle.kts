import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("mio.android.library")
}

extensions.configure<KotlinAndroidProjectExtension> {
    explicitApi()
}
