plugins {
    id("mio.android.library")
}

dependencies {
    implementation(project(":data:provider:api"))
    implementation(project(":core:network"))
}
