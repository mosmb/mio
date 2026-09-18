plugins {
    id("mio.android.library.compose")
    alias(libs.plugins.metro)
}

dependencies {
    api(project(":feature:progress:api"))
    implementation(project(":core:common"))
    implementation(project(":domain:media"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":domain:progress"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:sync"))
    implementation(project(":domain:model"))
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
}
