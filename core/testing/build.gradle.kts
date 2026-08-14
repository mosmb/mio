plugins {
    id("mio.kotlin.library")
}

dependencies {
    implementation(project(":domain:model"))
    implementation(project(":domain:account"))
    implementation(project(":domain:media"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:preferences"))
    implementation(project(":data:provider:api"))
    implementation(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
