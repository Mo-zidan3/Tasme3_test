# Commit B (detekt wiring) — HELD, ready to apply

**Why held**: the typed DetektExtension block in build.gradle.kts was never compiled locally —
the sandbox's Maven Central 429 blocks `./gradlew help` at plugin resolution ("could not resolve
plugin artifact 'org.jetbrains.kotlin.jvm:...:2.4.21'", V-035/V-037). Per the review instruction,
an unverified root build-script change must not be coupled to the (now fully green) evidence
pipeline. Apply ONLY when `./gradlew help` compiles locally.

**Payload** (apply to build.gradle.kts after the plugins block):
```kotlin
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension>("detekt") {
        toolVersion = rootProject.libs.versions.detekt.get()
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    }
}
```
Plus `config/detekt/detekt.yml`:
```yaml
build:
  maxIssues: 100000  # provisional ceiling — REPORT, not gate, until Mo's D-017 ratchet decision
```
Plus the CI step (build job, after ktlint):
```yaml
      - name: Static analysis — detekt (wired at root, all modules)
        run: ./gradlew detekt
```
When B lands: record the FIRST REAL detekt findings count for Mo's D-017 decision
(ratchet down / zero-lock / curate rules). Until then detekt is honestly ABSENT from CI.
