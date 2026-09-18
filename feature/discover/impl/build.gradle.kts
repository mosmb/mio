plugins {
    id("mio.android.library.compose")
    alias(libs.plugins.metro)
}

metro {
    generateContributionProviders.set(true)
}

dependencies {
    implementation(project(":feature:discover:api"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:media:api"))
    implementation(project(":domain:media"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:model"))
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
