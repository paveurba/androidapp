// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    // org.jetbrains.kotlin.android is no longer needed: AGP 9's built-in Kotlin
    // support (android.builtInKotlin=true in gradle.properties) compiles Kotlin
    // directly. Feature plugins like kotlin.plugin.compose still apply on top.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
