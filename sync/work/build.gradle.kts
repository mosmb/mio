plugins {
    id("mio.android.library")
    alias(libs.plugins.metro)
}

metro {
    generateContributionProviders.set(true)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":domain:sync"))
    implementation(project(":domain:model"))
    implementation(libs.work.runtime)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
