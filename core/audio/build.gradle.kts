plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.core.audio"
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
    implementation(project(":core:asr"))

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
