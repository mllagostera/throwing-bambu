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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "throwing-bambu"

include(":core")

// `:core` is plain Kotlin JVM (golden rule §0.1) and needs no Android SDK to compile or
// to test. `-PcoreOnly` leaves the Android modules out, so the engine — physics, RNG,
// scenario, AI, protocol — can be worked on in an environment with no SDK and no access
// to Google Maven:
//
//     ./gradlew -PcoreOnly :core:test
//
// The full build, the one CI runs, does not pass that property and includes all three.
if (!providers.gradleProperty("coreOnly").isPresent) {
    include(":transport")
    include(":app")
}
