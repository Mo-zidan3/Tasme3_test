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

// V-037/T3b: detekt wired for REAL (typed form; verified via CI on this branch — the sandbox's
// Maven Central 429 blocks local `./gradlew help`, but CI runners resolve fine).
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension>("detekt") {
        toolVersion = rootProject.libs.versions.detekt.get()
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    }
}
