plugins {
    id("mio.android.library")
    alias(libs.plugins.metro)
}

dependencies {
    implementation(project(":core:common"))
    api(libs.datastore.preferences)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
