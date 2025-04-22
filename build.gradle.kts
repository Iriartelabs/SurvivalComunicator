buildscript {
    val kotlinVersion = "1.8.0"
    extra["kotlin_version"] = kotlinVersion

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.android.tools.build:gradle:8.0.0")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.5.3")
    }
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}