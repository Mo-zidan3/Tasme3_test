plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.iqraa.core.asr"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
}

dependencies {
    // RecitationRecognizer contract (§6.2) is Android-free; implementation
    // wraps vendored com.k2fsa.sherpa.onnx Kotlin API (Apache-2.0, vendored
    // from sherpa-onnx v2.0.2 — see core/asr/src/main/kotlin/com/k2fsa/...).
    implementation(project(":core:align"))

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
    testImplementation(libs.truth)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
