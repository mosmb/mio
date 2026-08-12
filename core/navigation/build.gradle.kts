plugins {
    id("mio.android.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.navigation3.runtime)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.navigation3.ui)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
