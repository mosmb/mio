plugins {
    id("mio.android.library.api")
}

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":domain:model"))
}
