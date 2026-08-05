import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

plugins {
    id("mio.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<LibraryExtension> {
    buildFeatures {
        compose = true
    }
}

dependencies {
    val mioLibs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
    add("implementation", platform(mioLibs.findLibrary("compose-bom").get()))
    add("implementation", mioLibs.findLibrary("compose-ui").get())
    add("implementation", mioLibs.findLibrary("compose-foundation").get())
    add("implementation", mioLibs.findLibrary("compose-ui-tooling-preview").get())
    add("debugImplementation", mioLibs.findLibrary("compose-ui-tooling").get())
}
