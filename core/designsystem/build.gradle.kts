plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.iqraa.core.designsystem"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = 27
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.text)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit.jupiter)

    debugImplementation(libs.compose.ui.tooling)
    api(libs.compose.ui.tooling.preview)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
