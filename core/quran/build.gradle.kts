plugins {
    id("jacoco")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.truth)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jacoco {
    toolVersion = "0.8.13"
}

// V-036: XML report MUST be produced (Gradle 9 defaults xml.required=false in some setups —
// run 45061168092 proved the XML was absent and the coverage gate failed on missing files).
// V-037: typed named<JacocoReport> — the untyped tasks.named("…") has no `reports {}`
// accessor in Kotlin DSL (run 45061936775: "Unresolved reference 'reports'").
tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)
        html.required.set(true)
        // deterministic location the CI gate probes
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
    }
}

tasks.withType<Test> {
    extensions.configure<JacocoTaskExtension> {
        isEnabled = true
    }
}


// §6.1: core:quran is pure JVM (KMP-portable). The Tanzil asset is consumed by the
// app module; Phase 1 adds the processed derived tables here as pure-JVM resources.
