plugins {
    id("mio.android.library")
}

dependencies {
    implementation(project(":domain:preferences"))
    implementation(project(":domain:model"))
    implementation(project(":core:datastore"))
}
