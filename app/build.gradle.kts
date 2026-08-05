plugins {
    id("mio.android.application.compose")
}

dependencies {
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
}
