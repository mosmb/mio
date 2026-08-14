plugins {
    id("mio.android.library.compose")
}

dependencies {
    implementation(project(":core:designsystem"))
    api(libs.coil.core)
    implementation(libs.coil.compose.core)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coil.test)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
