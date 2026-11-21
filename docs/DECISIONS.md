# DECISIONS — ADR log

All version pins verified against primary sources on **2026-11-21**; evidence in `docs/VERIFICATION.md`.

## D-001 Toolchain pins (Phase 0)

| Component | Version | Rationale (source-verified) |
|---|---|---|
| Gradle | 9.9.2 | latest stable; AGP 9.5 requires ≥ 9.6.0 (AGP 9.5 release notes) |
| AGP | 9.5.2 | latest stable AGP (dl.google.com maven-metadata `<release>` chain, alpha excluded); max API 37 |
| Kotlin | 2.4.21 | latest stable Kotlin (Maven metadata; 2.5.0 line only has RC/Beta). Applied via AGP 9 **built-in Kotlin** for Android modules |
| KSP | 2.3.14 | latest stable KSP (KSP2 line). Applied via `plugins{}` with version; **must NOT be put on the buildscript classpath** (breaks AGP 9.5 — see ADR below) |
| Hilt | 2.60.1 | latest stable Dagger |
| compileSdk | 37 + minor 1 (`compileSdk = 37; compileSdkMinor = 1`) | Compose BOM 2026.11.01 (foundation/ui/animation 1.13.0) AAR metadata requires ≥ 37.1; AGP 9.5 supports max API 37 → 37.1 fits |
| minSdk / targetSdk | 26 / 37 | spec §4 |
| Build Tools | 36.0.0 | AGP 9.5 default |
| JDK | 17 (toolchain), Temurin 21 installed in sandbox | AGP 9 requires JDK ≥ 17 |

### ADR: KGP-on-buildscript-classpath is broken with AGP 9.5.2
AGP 9 docs ("Runtime dependency on KGP") recommend `buildscript { dependencies { classpath(kotlin-gradle-plugin) } }`
to raise KGP above the minimum. Empirically (probe project, 2026-11-21), ANY `kotlin-gradle-plugin` on the
buildscript classpath (2.2.21, 2.3.21, 2.4.21, 2.5.0-RC) makes `com.android.application` fail with
`NoClassDefFoundError: com/android/build/gradle/api/BaseVariant` via `KotlinAndroidTarget`. The working
configuration is: no buildscript classpath; apply `org.jetbrains.kotlin.plugin.compose`, `com.google.devtools.ksp`,
`com.google.dagger.hilt.android` (with versions, in the module `plugins{}` blocks via the version catalog).
KSP 2.3.14 resolves standalone (KSP2) without KGP on the classpath. Recorded as a deviation from the
documented path; re-open if AGP/KSP change.

## D-002 Module/dependency pins (Phase 0)
Compose BOM 2026.11.01; activity-compose 1.13.0; core-ktx 1.19.1; lifecycle 2.11.1;
navigation-compose 2.10.2; Room 2.8.5; DataStore 1.3.0; hilt-navigation-compose 1.4.0;
coroutines 1.10.2; JUnit Jupiter 6.1.3; Truth 1.4.5 (probe validated); detekt 1.23.8.
All latest stable from Maven metadata on 2026-11-21 (alpha/beta/RC excluded).

## D-003 sherpa-onnx pin (Spike S2)
- Release tag **v2.0.2** (published 2026-11-19; latest stable; evidence: GitHub releases API).
- jniLibs asset: `sherpa-onnx-v2.0.2-android.tar.bz2` (47.9 MB download; contains all ABIs).
  Extracted **only** `libonnxruntime.so` (22.2 MB) + `libsherpa-onnx-jni.so` (5.4 MB) for arm64-v8a
  into `app/src/main/jniLibs/arm64-v8a/` — per the asset's own README ("you only need libonnxruntime.so
  and libsherpa-onnx-jni.so"). SHA-256 of the archive:
  `08d22f7eb5c49bd161d248633eeb66e84735725706e21202b684800e5081a029`.
- Vendored Kotlin API from the same tag (`sherpa-onnx/kotlin-api/`): `OfflineRecognizer.kt`, `Vad.kt`,
  `FeatureConfig.kt`, `OfflineStream.kt`, `QnnConfig.kt`, `HomophoneReplacerConfig.kt`
  (the latter two are compile deps of `OfflineRecognizerConfig`).
  Package `com.k2fsa.sherpa.onnx` (Apache-2.0, notice in THIRD_PARTY_NOTICES.md).
- jniLibs APK packaging: dependency `.so` for other ABIs excluded via `packaging.jniLibs.excludes`
  (AGP 9 has no `defaultConfig.ndk.abiFilters` in the new DSL; verified empirically).
- API surface matches spec Q1-CLOSED: `OfflineRecognizerResult(text, tokens, timestamps, lang, emotion,
  event, durations, words)` — per-token timestamps, no confidence. `OfflineRecognizer(assetManager, config)`
  constructor confirmed in the vendored source.

## D-004 Model pins (Spike S1)
HF pinned revisions (from HF API `sha` at fetch time, 2026-11-21):
- `Abdou/quran-asr-small` @ `0c2744b9d70aaf45ba574c30c5d318c575258e66`
- `Abdou/quran-asr-tiny` @ `a291b33fd5a31cc4f212363ee1ba2d56d9710b49`
Files + SHA-256 in `scripts/fetch_models.py` (verified by `sha256sum`); models are NOT committed
(`app/src/main/assets/models/` git-ignored). Archive location for the exact shipped files +
license texts: **open question to Mo** (§8.2 / §18 — "Mo's storage / private release asset").
`tokens.txt` read and verified: 31 lines = blank(0) + 29 Arabic letters (ids 1..29) + `▁` word
separator (id 30). Same file ships in both repos (identical SHA-256).

## D-005 Quran text (Spike S4)
Tanzil **Uthmani** with ayah numbers (`quranType=uthmani`, `outType=txt-2`, marks/sajdah/rub enabled)
downloaded from tanzil.net download form (POST `/pub/download/index.php`).
- 1,370,878 bytes; **6,236 ayah lines; 114 surah ids** (verified by awk) — matches §7.3 expectation.
- SHA-256: `bf4f57b968d03f4131c070b1e285da9be0e0a108a21c910e872801ca273312c8`.
- Tanzil CC-BY-3.0 notice block present in-file (must be carried into derived files per Q5-CLOSED).
- Kept at `third_party/tanzil/quran-uthmani.txt` (git-ignored). Phase 1 commits the processed
  read-only asset with the notice preserved.

## D-006 Font (Spike S5)
Amiri **1.003** from upstream `aliftype/amiri` release (not Google Fonts — §7.7).
`AmiriQuran.ttf` (136,920 bytes) SHA-256 `e2a47644762d16bdfb6d33e0d8db8c6ff30beae84150ef5a705316bbd829455c`;
`OFL.txt` (SIL OFL 1.1) SHA-256 `72de68e5954f4fdd24702292ef5a32f003ca960ec9330dc86e5eefb5dffb9b22`.
Asset placed at `app/src/main/assets/fonts/AmiriQuran.ttf`.

## D-007 VAD (Spike S3)
`silero_vad.onnx` (632 KB) from sherpa-onnx releases tag `asr-models`
(https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx),
SHA-256 `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`.
Note (vs VAD-CLOSED): the asr-models release also carries silero_vad v4/v5 and ten-vad variants;
we pinned the plain `silero_vad.onnx` named by the spec. Config per §8.4/VAD-CLOSED;
`maxSpeechDuration` must be raised to ≥ `maxSegmentMs/1000` in code (Phase 2).

## D-008 Environment
Sandbox has no preinstalled Android SDK/Gradle/JDK-with-javac. Installed inside the workspace
(git-ignored): cmdline-tools (9862592), platforms;android-37.0 + android-37.1, build-tools;36.0.0
(.sdk/); Temurin JDK 21 (.jdk/). `local.properties` points sdk.dir at `.sdk` (not committed).
Disk: ~7 GB free after all downloads (models+SDK+JDK ≈ 2.6 GB used at the end of Phase 0).

## D-009 Build configuration
- `org.gradle.configuration-cache=false`: AGP+KSP under this Gradle 9.9.2 sandbox showed stale
  configuration-cache entries across script edits (probe); correctness preferred for Phase 0.
  Re-enable in Phase 1+ once the build stabilizes.
- Version catalog `gradle/libs.versions.toml` is the single source for versions.
- Debug APK measured **84 MB** (55 MB models + 26 MB jniLibs + 12.8 MB app&deps; debug is
  uncompressed). Release AAB with R8 + resource shrinking expected well below; the ≤80 MB
  download budget check happens in Phase 4 with the real AAB (not asserted now).

## D-010 Gradle wrapper checksum
gradle-wrapper.jar SHA-256 `621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8`
verified against the value published by services.gradle.org for 9.9.2 (docs/VERIFICATION.md).

## Open `[MO]` items surfaced (ratification list, §18)
- Presets §9.5 (Conservative default), summary semantics §9.6 — implemented as written.
- Font §7.7 Amiri default; KFGQPC only after Mo provides its EULA.
- tiny shipping policy §8.1 (badge «الوضع الخفيف» when active).
- applicationId still `com.example.tasmee` (dev placeholder; Phase 4 Play blocker).
- Archive location for shipped model files + license texts (asked once; local until answered).
