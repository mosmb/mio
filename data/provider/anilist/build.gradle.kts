plugins {
    id("mio.android.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":data:provider:api"))
    implementation(project(":core:network"))
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:testing"))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
