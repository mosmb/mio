plugins {
    id("mio.android.library")
}

dependencies {
    implementation(project(":domain:media"))
    implementation(project(":domain:model"))
    implementation(project(":core:database"))
    implementation(project(":data:provider:api"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.room.runtime)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}

android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
