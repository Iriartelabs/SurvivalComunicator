pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
    plugins {
        // Android Gradle Plugin
        id("com.android.application") version "8.0.2" apply false

        // Kotlin + KAPT + Serialization
        kotlin("android")              version "1.8.22" apply false
        kotlin("kapt")                 version "1.8.22" apply false
        kotlin("plugin.serialization") version "1.8.22" apply false

        // Navigation Safe Args
        id("androidx.navigation.safeargs.kotlin") version "2.5.3" apply false
    }

}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "SurvivalComunicator"
include(":app")