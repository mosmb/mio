plugins {
    id("mio.android.library")
}

dependencies {
    api(project(":domain:progress"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:model"))
    implementation(project(":core:database"))
    implementation(project(":data:sync"))
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
