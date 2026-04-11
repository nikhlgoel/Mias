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
    }
}

rootProject.name = "Kid"

include(":app")
include(":core:common")
include(":core:data")
include(":core:inference")
include(":core:network")
include(":core:thermal")
include(":core:soul")
include(":core:security")
include(":core:ui")
include(":core:model-hub")
include(":core:agent")
include(":core:evolution")
include(":core:resilience")
include(":core:speech")
