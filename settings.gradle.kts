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

// `:core` es Kotlin JVM puro (regla de oro §0.1) y no necesita el SDK de Android para
// compilarse ni testearse. `-PcoreOnly` deja fuera los módulos Android para poder
// trabajar el núcleo —física, RNG, escenario, IA— en un entorno sin SDK ni acceso a
// Google Maven:
//
//     ./gradlew -PcoreOnly :core:test
//
// El build completo (el que ejecuta CI) no pasa esa propiedad e incluye los tres módulos.
if (!providers.gradleProperty("coreOnly").isPresent) {
    include(":transport")
    include(":app")
}
