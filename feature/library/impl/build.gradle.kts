plugins {
    id("mio.android.library.compose")
}

dependencies {
    implementation(project(":feature:library:api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:media:api"))
    implementation(project(":feature:progress:api"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:progress"))
    implementation(project(":domain:preferences"))
    implementation(project(":domain:model"))
}
