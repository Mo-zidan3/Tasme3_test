plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.iqraa.core.data"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = 27
    }

    // §15: exportSchema=true — schemas land in core/data/schemas for CI migration tests.
    ksp {
        arg("room.schema.location", "$projectDir/schemas")
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
    implementation(project(":core:quran"))
    implementation(project(":core:align"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Room (§15): exportSchema=true, versioned migrations, no destructive fallback
    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)
    api(libs.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    testImplementation(libs.room.testing)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
