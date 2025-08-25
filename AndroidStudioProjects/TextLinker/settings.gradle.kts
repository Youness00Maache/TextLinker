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
    plugins {
        // Fixed: Use Kotlin-style parentheses and double quotes
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
    }
}

dependencyResolutionManagement {
    // This will still show incubating warnings but won't break the build
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TextLinker"
include(":app")