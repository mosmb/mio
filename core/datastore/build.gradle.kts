plugins {
    id("mio.android.library")
}

dependencies {
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
