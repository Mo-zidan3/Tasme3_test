plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.iqraa.tasmee"   // §4: dev placeholder; Play release blocker — Mo must supply final id (§18)
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.iqraa.tasmee"
        // D-012/D-013: 26 → 27. Measured floor (V-016): the pinned onnxruntime
        // libonnxruntime.so carries .note.android.ident api_level 0x1b (27). Loading it on
        // API 26 is undefined. CI asserts minSdk >= max(api_level) over every shipped .so.
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    // §4 + D-013: RELEASE ships arm64-v8a only. The DEBUG source set additionally carries the
    // x86_64 pair (app/src/debug/jniLibs, fetched by scripts/fetch_native.py, git-ignored,
    // SHA-pinned) so the CI emulator (x86_64 image) can System.loadLibrary it. The excludes
    // below are scoped to RELEASE builds only; debug APK = arm64 + x86_64.
    packaging {
        jniLibs {
            excludes += listOf(
                "lib/armeabi-v7a/**",
                "lib/x86/**",
            )
        }
    }
    // Release-only x86_64 strip (D-013): packaging{} is variant-global in the AGP 9 DSL, so
    // the strip is expressed per-variant through androidComponents' packaging extension
    // (fallback: the CI ABI check fails the build if release ever ships x86_64).
    androidComponents {
        onVariants(selector().withBuildType("release")) { variant ->
            variant.packaging.jniLibs.excludes.add("lib/x86_64/**")
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
        // T10 (V-045): releaseSmoke proves the R8-KEPT build decodes on device — inherits
        // release EXACTLY (minify + shrink + the SAME proguard files) but is debug-signed
        // so the emulator can install it. connectedReleaseSmokeAndroidTest runs a minimal
        // decode assertion on the committed 54:10 fixture; if R8 strips a JNI entry point
        // or a serialization shape the recognizer needs, this fails LOUDLY. Never disable
        // minify to make it pass — fix the keep rules.
        create("releaseSmoke") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
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

    androidTestImplementation(project(":core:data"))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    // BOM platforms: the versionless compose test artifacts resolve through them (run
    // 45335602431: "Could not find androidx.compose.ui:ui-test-junit4:." — no version)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
