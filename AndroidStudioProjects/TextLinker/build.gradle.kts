// Top-level build.gradle.kts
plugins {
    // Version catalog aliases
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    // Navigation Safe Args (not in version catalog)
    id("androidx.navigation.safeargs") version "2.7.7" apply false
}

buildscript {
    repositories {
        google()
    }
    dependencies {
        // Required for navigation safeargs plugin
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.5.3")
    }
}