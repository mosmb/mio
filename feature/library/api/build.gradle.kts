plugins {
    id("mio.android.library.api")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":domain:model"))
    implementation(libs.navigation3.runtime)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit4)
}
