plugins {
    id("mio.kotlin.domain")
}

dependencies {
    api(project(":domain:media"))
    api(project(":domain:collection"))
    api(project(":domain:model"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
