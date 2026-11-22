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

## D-011 Phase 1 decisions (2026-11-21)
- N() (§7.4): derived VERBATIM from the model repo's own normalizer (`retrieve.py` at pinned rev
  0c2744b…): fold table {أ/إ/آ/ٱ/ٲ/ٳ→ا, ى/ئ/ے/ی→ي, ؤ→و, ة→ه, ک→ك}; drop every non-letter
  (harakat, 0653–0655, 0670, tatweel, 06E5/06E6, Quranic marks 06D6–06ED). NOT invented. ۥ/ۦ are
  dropped as marks (not folded to و/ي) — matches the author's letters(); verified over the full
  text (V-020). Alef-wasla ٱ→ا. No tanween-split rejoin for Tanzil (0 cases; author's rule targets
  their reference source).
- Tanzil asset committed at core/quran/src/main/resources/quran-uthmani.txt with the VERBATIM
  notice header (28-line block byte-for-byte, sha256 pinned in QuranText). Integrity tests enforce
  114/6,236/checksum/no-mutation. CC-BY-3.0 interpretation recorded per §7.1 (D-011 cross-ref V-019).
- core:align semantics resolved during testing (all traceable to §9):
  - Semi-global DP: free leading/trailing OBSERVED speech AND free leading EXPECTED skips
    (recitation starts mid-back-window); interior expected skips cost gapMissed; trailing
    expected are simply not-yet-recited (best-end scan may end at j<m).
  - Substitution of a high-sim neighbor word is legitimate DP behavior (e.g. skipping الرحمن then
    reciting الرحيم yields CLOSE for the first + CORRECT for the second); the tracker prefers
    whatever maximizes total alignment score — no special-casing.
  - Absence evidence (expected skipped inside a matched span) counts ONLY toward MISSED
  (≥2 independent), never toward WRONG — a skip is not a misrecitation (bug fixed, V-021).
  - An all-EXTRA observation (repeat behind window / ta'awwudh) records EXTRA stats and feeds the
  lost timer (not discarded) (bug fixed, V-021).
  - Boundary exclusion: pause-FINALs relax interiority per §9.2 (verified by test); partials keep it.
- Wrong-word and missed-skip tests require CONTEXT words: an isolated single wrong word is
  legitimately discarded by the semi-global free-edge (score ≤ 0 → pure EXTRA). Recorded as
  harness-realism note (V-022) — real partial windows (partialWindowMs=2500) provide context.
- tools:eval pins kotlinx-serialization-json 1.12.0 (latest stable, Maven-verified) with the
  Kotlin serialization plugin (2.4.21, same Kotlin line).
- §7.5 N() self-validation PENDING: protocol requires a real clean fixture; none exists in the
  sandbox. N()↔author-normalizer equivalence verified on the full text instead. No CER claimed.
- JaCoCo 0.8.13 for coverage measurement; targets MET (91.9% combined).

## D-012 Phase-2 host-side + minSdk ADR (2026-11-21)
- **ADR: raise minSdk 26 → 27.** Measured basis (V-016): the prebuilt `libonnxruntime.so`
  carries `.note.android.ident` with API floor 27 (NDK r27d); loading it on API 26 is
  undefined. Raising to 27 (Android 8.1) excludes only Android 8.0 devices (API 26) — a small
  slice. This is a §4 deviation forced by a pinned dependency, flagged for Mo ratification;
  verified on the API-27 emulator entry of the CI instrumented job (docs/CI.md) the first time
  it runs. app/build.gradle.kts will change to `minSdk = 27` in Phase 3 (UI) together with the
  emulator evidence, to keep this commit host-side only. [MO] ratification requested.
- **D-013 trailing-EXTRA root cause** (renumbered from the ad-hoc D-025; 2026-11-22) (aligner, not generator): finals aligned against
  pointer−backWords only. Fixed: finals align with lo=0 (free leading expected skips). The
  synthetic report regenerated: 19/19 CORRECT, MISSED + WRONG fire, hit-rate 96.5%.
- **Scheduler law implemented** (core:audio): effective cadence = max(partialEveryMs, RTF×W);
  queue depth 1 (final never starves); stale-drop by windowEndMs (§6.2). 13 tests, fake RTFs
  0.2/0.3/0.5/0.9/1.4 asserted.
- **core:asr decode implemented** (host-side): greedy CTC via vendored sherpa API
  (createStream/acceptWaveform(pcm, 16000)/decode/getResult), word assembly between ▁ with
  seconds×1000 + FRAME_SHIFT_MS=40 (V-015), null-timing fallback (5 tests).
- **core:session orchestration + calibration**: fake-recognizer driven session; checkpoint
  after each FINAL (§11); calibration = 3 probes, discard first, median → RTF → L0–L3 ladder
  (measured triggers only) + model choice; badge («الوضع الخفيف») from L2 (§8.6/§10.5).
- **D-014 ABI source sets (2026-11-22)**: release ships arm64-v8a only (D-003); the x86_64
  pair from the SAME pinned tarball (sha256 087e…5c3e / cdeb…52a8) is extracted into
  app/src/debug/jniLibs/ ONLY (git-ignored) so the CI x86_64 emulator can System.loadLibrary.
  Implemented via `androidComponents.onVariants(release)` packaging exclude `lib/x86_64/**`;
  debug = arm64 + x86_64. Guards: CI unzip assertions on both APKs + check_abi_floor.py
  (minSdk 27 >= max api_level 27, both ABIs measured). Tarball paths begin "./jniLibs/…" —
  extraction startswith() fixed (masked earlier by the sidecar skip).
- **D-015 minSdk applied (2026-11-22)**: app/build.gradle.kts minSdk 26 → 27 now (was deferred
  to Phase 3 — the known API-26 load failure could not ship even to CI). CI asserts
  minSdk >= max(.note.android.ident api_level) over every shipped .so via
  scripts/check_abi_floor.py so a jniLibs bump cannot regress silently. [MO] ratification
  stays open (D-012).
- **D-016 CI timing fixture (2026-11-22)**: scripts/make_timing_fixture.py regenerates the
  V-015 harmonic-complex 8 s / 16 kHz WAV deterministically (code, not a binary; sha256
  da6798…ec43 verified equal across runs). The instrumented timing test decodes it on-device
  and asserts seconds-scale timestamps + 40 ms grid. Mo's own clip remains the separate
  loud-skip ACCURACY test. No gated/third-party audio ever committed.
- Size options table for Mo (PENDING the CI bundletool measurement — numbers land with the
  first remote run): (a) PAD install-time pack [VERIFY AssetManager access + no INTERNET
  needed], (b) drop tiny −15.6 MB stored (kills the low-end fallback — interacts with §8.6),
  (c) accept with ADR. Do NOT decide before the measurement.

## D-017 — detekt ceiling vs zero-findings [PENDING Mo; wiring = commit B, HELD]
detekt 1.23.8 wiring moved to commit B (typed `extensions.configure<DetektExtension>` at the
root; the untyped setProperty form from V-035 was never compiled and is removed). Commit B is
held until `./gradlew help` compiles locally — an unverified root build-script change must not
be coupled to the evidence-gathering run (it could kill every job at configuration, including
the currently-green release job). When B lands: maxIssues 100000 ceiling so legacy findings do
not instantly redden CI — a REPORT, not a gate, until Mo picks (a) ratchet down, (b) zero-lock,
(c) curate rules.

## D-018 — size job merged into release; instrumented on ubuntu-24.04 (V-035) [APPLIED]
The standalone `size` job raced the release-outputs artifact (run 45035800627: "Artifact
not found for name: release-outputs"). bundletool 1.17.0 (sha256-pinned) now runs inside
release AFTER bundleRelease: no race by construction, one less job setup, measurement +
budget gates unchanged (≤80 MB warn / >85 MB fail). instrumented moved to ubuntu-24.04
with a mandatory /dev/kvm pre-flight (ubuntu-26.04 images carry no KVM; emulator refuses
-accel off). TCG fallback explicitly rejected pending Mo's sign-off for long runs; if no
hosted KVM runner is available the escalation is macOS arm64 (10× minutes) or Mo's device.
