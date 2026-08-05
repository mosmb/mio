plugins {
    id("mio.android.library")
}

dependencies {
    implementation(project(":domain:sync"))
    implementation(project(":domain:model"))
}
