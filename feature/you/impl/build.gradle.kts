plugins {
    id("mio.android.library.compose")
}

dependencies {
    implementation(project(":feature:you:api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":domain:account"))
    implementation(project(":domain:sync"))
    implementation(project(":domain:preferences"))
    implementation(project(":domain:model"))
}
