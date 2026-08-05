plugins {
    id("mio.android.library")
}

dependencies {
    implementation(project(":domain:sync"))
    implementation(project(":domain:model"))
    implementation(project(":core:database"))
    implementation(project(":data:provider:api"))
}
