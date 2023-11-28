// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.1.4" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.github.ben-manes.versions") version "0.50.0"

    // Make sure that you have the Google services Gradle plugin dependency
    id("com.google.gms.google-services") version "4.4.0" apply false

    // Add the dependency for the Crashlytics Gradle plugin
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}

tasks.named<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates") {
    resolutionStrategy {
        componentSelection {
            all {
                val rejectedKeywords = listOf("alpha", "beta", "rc", "m", "snapshot")
                for (keyword in rejectedKeywords) {
                    if (candidate.version.contains(keyword, ignoreCase = true)) {
                        reject("Version contains non-stable keyword: $keyword")
                        break
                    }
                }
            }
        }
    }
}
