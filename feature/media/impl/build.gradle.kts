plugins {
    id("mio.android.library.compose")
}

dependencies {
    implementation(project(":feature:media:api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:progress:api"))
    implementation(project(":domain:media"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:progress"))
    implementation(project(":domain:model"))
}
