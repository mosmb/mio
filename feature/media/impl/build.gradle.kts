plugins {
    id("mio.android.library.compose")
    alias(libs.plugins.metro)
}

dependencies {
    api(project(":feature:media:api"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:progress:api"))
    implementation(project(":domain:media"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:progress"))
    implementation(project(":domain:model"))
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.compose.animation.core)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
