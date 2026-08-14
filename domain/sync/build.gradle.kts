plugins {
    id("mio.kotlin.domain")
}

dependencies {
    api(project(":domain:model"))
    api(project(":domain:collection"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
