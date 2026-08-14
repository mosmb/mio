plugins {
    id("mio.android.library")
    alias(libs.plugins.metro)
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
}
