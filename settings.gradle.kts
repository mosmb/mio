import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Mio"

includeBuild("build-logic")

include(":app")

include(":feature:onboarding:api")
include(":feature:today:api")
include(":feature:library:api")
include(":feature:discover:api")
include(":feature:you:api")
include(":feature:media:api")
include(":feature:progress:api")

include(":feature:onboarding:impl")
include(":feature:today:impl")
include(":feature:library:impl")
include(":feature:discover:impl")
include(":feature:you:impl")
include(":feature:media:impl")
include(":feature:progress:impl")

include(":domain:model")
include(":domain:account")
include(":domain:media")
include(":domain:collection")
include(":domain:progress")
include(":domain:queue")
include(":domain:sync")
include(":domain:preferences")

include(":data:provider:api")
include(":core:common")
include(":core:testing")
include(":architecture-tests")

include(":data:account")
include(":data:media")
include(":data:collection")
include(":data:progress")
include(":data:sync")
include(":data:preferences")
include(":data:provider:anilist")
include(":data:provider:myanimelist")

include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:logging")
include(":sync:work")
include(":core:designsystem")
include(":core:ui")
include(":core:navigation")
