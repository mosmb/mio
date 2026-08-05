plugins {
    id("mio.android.library.compose")
}

dependencies {
    implementation(project(":feature:progress:api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":domain:progress"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:sync"))
    implementation(project(":domain:model"))
}
