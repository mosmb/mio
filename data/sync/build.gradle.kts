plugins {
    id("mio.android.library")
    alias(libs.plugins.metro)
}

dependencies {
    api(project(":domain:sync"))
    implementation(project(":domain:model"))
    implementation(project(":core:database"))
    implementation(project(":core:common"))
    implementation(project(":data:provider:api"))

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
