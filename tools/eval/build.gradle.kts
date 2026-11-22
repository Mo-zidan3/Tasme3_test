plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21"
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    application
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
    // §13.2: tools:eval must use the SAME core:align code as the app.
    implementation(project(":core:align"))
    implementation(project(":core:quran"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    implementation(libs.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

application {
    mainClass.set("com.iqraa.tools.eval.EvalMainKt")
}
