// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9 has built-in Kotlin support, so the org.jetbrains.kotlin.android plugin is
// gone and the Kotlin version is selected here rather than in the plugins block.
// AGP would otherwise pin KGP 2.2.10, which is below what KSP 2.3 and the app's
// dependencies need.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.14")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    // Compose compiler ships with Kotlin from 2.0, so its version must match.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
