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

rootProject.name = "tasmee"

include(":app")
include(":core:quran")
include(":core:align")
include(":core:asr")
include(":core:audio")
include(":core:session")
include(":core:data")
include(":core:designsystem")
include(":feature:reader")
include(":feature:recite")
include(":feature:history")
include(":feature:settings")
include(":tools:eval")
