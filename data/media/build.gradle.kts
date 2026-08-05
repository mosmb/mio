plugins {
    id("mio.android.library")
}

dependencies {
    implementation(project(":domain:media"))
    implementation(project(":domain:model"))
    implementation(project(":core:database"))
    implementation(project(":data:provider:api"))
}
