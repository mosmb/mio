plugins {
    id("mio.android.library")
}

dependencies {
    implementation(project(":domain:progress"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:model"))
    implementation(project(":core:database"))
    implementation(project(":data:provider:api"))
    implementation(project(":data:sync"))
}
