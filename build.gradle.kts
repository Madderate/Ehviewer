plugins {
    id("com.android.application") version "8.0.2" apply false
    kotlin("android") version "1.9.0-Beta" apply false
    kotlin("plugin.serialization") version "1.9.0-Beta" apply false
    id("com.google.devtools.ksp") version "1.9.0-Beta-1.0.11" apply false
    id("com.mikepenz.aboutlibraries.plugin") version "10.7.0" apply false
}

allprojects {
    apply(from = "$rootDir/ktlint.gradle.kts")
}

tasks.register("Delete", Delete::class) {
    delete(rootProject.buildDir)
}

buildscript {
    dependencies {
        classpath("com.android.tools:r8:8.2.12-dev")
        classpath("org.mozilla.rust-android-gradle:plugin:0.9.3")
    }
}
