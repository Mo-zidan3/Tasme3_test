plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.tasmee"   // §4: dev placeholder; Play release blocker — Mo must supply final id (§18)
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.example.tasmee"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    // §4: arm64-v8a only for release — enforced via packaging (AGP 9 DSL)
    androidResources {
        // (no ndk block in AGP 9 new DSL; abiFilters move handled below)
    }
    packaging {
        jniLibs {
            // §4: arm64-v8a only — strip .so that androidx deps ship for other ABIs
            // (our own jniLibs dir contains arm64-v8a only)
            excludes += listOf(
                "lib/armeabi-v7a/**",
                "lib/x86/**",
                "lib/x86_64/**",
            )
        }
    }

    buildTypes {
        release {
            // Full R8 minification requires more RAM than the 4 GB sandbox allows
            // (kernel OOM-killed the daemon during minifyReleaseWithR8; evidence in
            // docs/VERIFICATION.md). CI (ubuntu-latest) runs with minify on.
            // Locally pass -PsandboxNoMinify to skip R8 for a smoke release build.
            isMinifyEnabled = !project.hasProperty("sandboxNoMinify")
            isShrinkResources = !project.hasProperty("sandboxNoMinify")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
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
    implementation(project(":core:designsystem"))
    implementation(project(":core:quran"))
    implementation(project(":core:align"))
    implementation(project(":core:asr"))
    implementation(project(":core:audio"))
    implementation(project(":core:session"))
    implementation(project(":core:data"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:recite"))
    implementation(project(":feature:history"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
}
