plugins {
    id("mio.android.application.compose")
    alias(libs.plugins.metro)
}

android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(project(":data:provider:api"))
    implementation(project(":domain:model"))
    implementation(project(":domain:account"))
    implementation(project(":domain:media"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:preferences"))
    implementation(libs.kotlinx.coroutines.core)

    implementation(project(":feature:onboarding:impl"))
    implementation(project(":feature:today:impl"))
    implementation(project(":feature:library:impl"))
    implementation(project(":feature:discover:impl"))
    implementation(project(":feature:you:impl"))
    implementation(project(":feature:media:impl"))
    implementation(project(":feature:progress:impl"))
    implementation(project(":data:account"))
    implementation(project(":data:media"))
    implementation(project(":data:collection"))
    implementation(project(":data:progress"))
    implementation(project(":data:sync"))
    implementation(project(":data:preferences"))
    implementation(project(":data:provider:anilist"))
    implementation(project(":data:provider:myanimelist"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":sync:work"))
    implementation(libs.navigation3.ui)

    testImplementation(project(":core:testing"))
    testImplementation(libs.room.testing)
    testImplementation(libs.datastore.preferences)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
