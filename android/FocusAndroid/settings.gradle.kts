pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }
    }
}

rootProject.name = "FocusAndroid"
include(":app")
include(":feature:video")
include(":feature:camera")
include(":core:ai")
include(":core:metadata")
include(":core:media")
include(":core:grpc")
include(":core:streaming")
include(":core:network")
include(":core:ui")
include(":feature:auth")
include(":feature:account")
include(":feature:broadcast")
