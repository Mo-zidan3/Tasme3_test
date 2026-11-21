// Top-level build file. Plugin versions are declared in gradle/libs.versions.toml
// and applied per-module. NOTE: AGP 9 built-in Kotlin must NOT be combined with
// a Kotlin Gradle Plugin on the buildscript classpath (verified in spike probe —
// it breaks AGP 9.5 with `com/android/build/gradle/api/BaseVariant` errors).
// Pure-JVM modules apply org.jetbrains.kotlin.jvm with a version from the catalog.
plugins {
    // versions resolved via catalog; apply=false keeps them off this project
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
}
