import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder

plugins {
    id("mio.android.library")
    alias(libs.plugins.metro)
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as HasHostTestsBuilder)
            .hostTests
            .getValue(HostTestBuilder.UNIT_TEST_TYPE)
            .enable = true
    }
}

dependencies {
    implementation(project(":core:common"))
    debugImplementation(libs.timber)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
}
