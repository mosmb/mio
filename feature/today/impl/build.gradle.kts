plugins {
    id("mio.android.library.compose")
    alias(libs.plugins.metro)
}

metro {
    generateContributionProviders.set(true)
}

dependencies {
    implementation(project(":feature:today:api"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:media:api"))
    implementation(project(":feature:library:api"))
    implementation(project(":domain:queue"))
    implementation(project(":domain:media"))
    implementation(project(":domain:progress"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:preferences"))
    implementation(project(":domain:model"))
    implementation(project(":domain:sync"))
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.metrox.viewmodel.compose)

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
