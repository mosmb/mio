plugins {
    id("mio.android.library")
    alias(libs.plugins.metro)
}

dependencies {
    implementation(project(":domain:preferences"))
    implementation(project(":domain:model"))
    implementation(project(":core:datastore"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
