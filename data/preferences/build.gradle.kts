plugins {
    id("mio.android.library")
}

dependencies {
    implementation(project(":domain:preferences"))
    implementation(project(":domain:model"))
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
