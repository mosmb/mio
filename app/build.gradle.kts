import java.util.Properties

plugins {
    id("mio.android.application.compose")
    alias(libs.plugins.metro)
}

metro {
    generateContributionProviders.set(true)
}

val mioLocalProperties =
    Properties().apply {
        val propertiesFile = rootProject.file("local.properties")
        if (propertiesFile.isFile) {
            propertiesFile.inputStream().use(::load)
        }
    }

fun oauthClientId(
    gradlePropertyName: String,
    environmentVariableName: String,
): String =
    providers.gradleProperty(gradlePropertyName).orNull
        ?: providers.environmentVariable(environmentVariableName).orNull
        ?: mioLocalProperties.getProperty(gradlePropertyName).orEmpty()

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val mioTestInstrumentationRunner =
    providers.gradleProperty("mio.testRunner").orNull
        ?: "androidx.test.runner.AndroidJUnitRunner"

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        testInstrumentationRunner = mioTestInstrumentationRunner
        buildConfigField(
            "String",
            "MIO_ANILIST_CLIENT_ID",
            oauthClientId("mio.anilist.clientId", "MIO_ANILIST_CLIENT_ID").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "MIO_MYANIMELIST_CLIENT_ID",
            oauthClientId("mio.myanimelist.clientId", "MIO_MYANIMELIST_CLIENT_ID").asBuildConfigString(),
        )
    }
    lint {
        // Task 08 deliberately keeps the v26 adaptive-icon source beside its v33 overlay.
        disable += setOf("ObsoleteSdkInt")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(project(":core:logging"))
    implementation(project(":core:ui"))
    implementation(project(":data:provider:api"))
    implementation(project(":domain:model"))
    implementation(project(":domain:sync"))
    implementation(project(":domain:account"))
    implementation(project(":domain:media"))
    implementation(project(":domain:collection"))
    implementation(project(":domain:preferences"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.metrox.viewmodel.compose)

    implementation(project(":feature:onboarding:impl"))
    implementation(project(":feature:today:impl"))
    implementation(project(":feature:library:impl"))
    implementation(project(":feature:discover:impl"))
    implementation(project(":feature:you:impl"))
    implementation(project(":feature:media:impl"))
    implementation(project(":feature:progress:impl"))
    implementation(project(":data:account"))
    implementation(project(":data:media"))
    implementation(project(":data:collection"))
    implementation(project(":data:progress"))
    implementation(project(":data:sync"))
    implementation(project(":data:preferences"))
    implementation(project(":data:provider:anilist"))
    implementation(project(":data:provider:myanimelist"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":sync:work"))
    implementation(libs.core.splashscreen)
    implementation(libs.navigation3.ui)
    implementation(libs.work.runtime)
    implementation(libs.coil.network.okhttp)

    testImplementation(project(":core:testing"))
    testImplementation(libs.room.testing)
    testImplementation(libs.datastore.preferences)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.compose.ui.test.manifest)
}
