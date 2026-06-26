pluginManagement {
    // React Native ships its Gradle plugin as source under the RN npm package.
    // Including it as a build makes `com.facebook.react[.settings|.rootproject]`
    // plugin ids resolvable in this single root build (the /mobile app + :core:*).
    includeBuild("mobile/node_modules/@react-native/gradle-plugin")
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

// RN autolinking. Default working dir is rootDir/../ which is wrong here (root =
// repo root), so point it at /mobile where package.json + node_modules live.
plugins {
    id("com.facebook.react.settings")
}
extensions.configure<com.facebook.react.ReactSettingsExtension> {
    autolinkLibrariesFromCommand(workingDirectory = file("mobile"))
}

dependencyResolutionManagement {
    // PREFER_SETTINGS (not FAIL_ON_PROJECT_REPOS): the RN Gradle plugin injects a
    // few project-level repos (the local RN/Hermes AARs); settings repos win.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // React Native + Hermes/JSC AARs are local maven inside the RN npm package.
        maven { url = uri("${rootDir}/mobile/node_modules/react-native/android") }
        maven { url = uri("${rootDir}/mobile/node_modules/jsc-android/dist") }
    }
}

rootProject.name = "Mias"

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
include(":core:language")

// The React Native app (Android). JS/TS lives at /mobile; the Android module is
// /mobile/android/app, built here in the one root Gradle build alongside :app and
// :core:*. Legacy :app stays installable until the RN cutover (strangler-fig).
include(":mobile")
project(":mobile").projectDir = file("mobile/android/app")
