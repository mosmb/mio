plugins {
    id("mio.android.library.compose")
    alias(libs.plugins.metro)
}

metro {
    generateContributionProviders.set(true)
}

dependencies {
    api(project(":feature:you:api"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":domain:account"))
    implementation(project(":domain:sync"))
    implementation(project(":domain:preferences"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:progress"))
    implementation(project(":domain:model"))
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
