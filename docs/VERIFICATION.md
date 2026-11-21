# VERIFICATION — evidence log

Every `[VERIFY]` item from the spec gets an entry here: date, source, command/output.
Author-reported numbers are never ours; our own measurements are labeled as such.

## 2026-11-21 — Phase 0 / 0.5 spike

### V-001 Toolchain versions (source: Maven/Goggle metadata, curl)
```
$ curl -s https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml | grep -oE "<version>[^<]+</version>" | sed 's/<[^>]*>//g' | grep -vE "alpha|beta|rc|RC" | tail -1
9.5.2
$ curl -s https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml | ... | tail (stable only)
2.4.21
$ curl -s https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml | ... | tail -1
2.3.14
$ curl -s https://services.gradle.org/versions/current | jq .version
"9.9.2"  (checksum 971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b, wrapperChecksum 621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8)
```

### V-002 AGP 9.5 compatibility (source: developer.android.com AGP 9.5 release notes)
Gradle min 9.6.0 · SDK Build Tools 36.0.0 · JDK 17 · max API 37. Compose BOM 2026.11.01
AAR metadata (error output during probe build) requires compileSdk ≥ 37.1 → installed
`platforms;android-37.1`; used `compileSdk=37` + `compileSdkMinor=1` (DSL verified from
AGP 9.0 gradle-api docs: `compileSdkMinor` property, added 8.10.1).

### V-003 KGP-on-classpath breakage (own probe measurement)
Probe project `/tmp/probe` with AGP 9.5.2:
- ✅ built-in Kotlin only + compose plugin (version in plugins{}) → BUILD SUCCESSFUL (APK 12.8 MB)
- ❌ any `classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:…")` (tried 2.2.21, 2.3.21, 2.4.21, 2.5.0-RC)
  → `Could not create an instance of type org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget …
  com/android/build/gradle/api/BaseVariant`
- ✅ `id("com.google.devtools.ksp") version "2.3.14"` in plugins{} + KSP/Hilt processors → BUILD SUCCESSFUL
- ✅ Hilt 2.60.1 KSP compile of @Singleton/@Component → generated code works
→ Project uses the ✅ configuration (ADR in DECISIONS D-001).

### V-004 sherpa-onnx v2.0.2 assets (source: GitHub releases API + archive inspection)
```
release tags (releases?per_page=8): v2.0.2 stable 2026-11-19 (latest stable)
asset "sherpa-onnx-v2.0.2-android.tar.bz2" 47.9 MB — sha256 08d22f7eb5c49bd161d248633eeb66e84735725706e21202b684800e5081a029
archive contains jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}/ — arm64: libonnxruntime.so, libsherpa-onnx-jni.so, libsherpa-onnx-c-api.so, libsherpa-onnx-cxx-api.so
archive README.md: "you only need libonnxruntime.so and libsherpa-onnx-jni.so"
```
Kotlin API vendored from tag v2.0.2 path `sherpa-onnx/kotlin-api/` — files:
OfflineRecognizer.kt, Vad.kt, FeatureConfig.kt, OfflineStream.kt, QnnConfig.kt,
HomophoneReplacerConfig.kt (the last two are required deps of OfflineRecognizerConfig —
discovered via compile error `Unresolved reference 'QnnConfig'`, then fetched from the same tag).
minSdk of the prebuilt .so: NOT stated in the release. Since the release tarball ships all
Android ABIs and sherpa-onnx's own Android examples run on API 21+, API 26 (our minSdk) is
expected to be fine, but **fixture decode on an API 26 emulator is still pending** (no emulator
in this sandbox; S2's "decode fixture on API 26 emulator" moves to Phase 2 first on-device
gate, flagged in STATUS).

### V-005 tokens.txt (spec Q3/Q8 — exact inventory read, not reconstructed)
```
$ cat third_party/…/tokens.txt   (identical file in both model repos, sha256 75b53814499cee84a07c7b7a318524d30bcaf84273c0f8b28a04176023e8c74f)
<blk> 0
ء 1  ا 2  ب 3  ت 4  ث 5  ج 6  ح 7  خ 8  د 9  ذ 10
ر 11 ز 12 س 13 ش 14 ص 15 ض 16 ط 17 ظ 18 ع 19 غ 20
ف 21 ق 22 ك 23 ل 24 م 25 ن 26 ه 27 و 28 ي 29
▁ 30
```
31 entries = 29 letters + `▁` separator + `<blk>` blank(0). **Matches V1/Q3-CLOSED.**
Documented for `core:quran` Phase 1 use (this file is the N() fold source of truth).

### V-006 Model downloads + SHA-256 (own measurement)
```
small model.int8.onnx 36,081,762 B  sha256 88f9df290d57b8890275d79187b77515aeeb12626a42bc76cbd0f8bfcaff00f0
small tokens.txt 180 B            sha256 75b53814499cee84a07c7b7a318524d30bcaf84273c0f8b28a04176023e8c74f
small spelling.json 69,970 B     sha256 7adff00aa1d4b0413a3a89f898117682a22639624b3666121f6de785e0fb346a
small transcribe.py 1,926 B      sha256 07251e030f9bc0161c10869f00f19dfa2951a97f954157aa5850783b049dfe72
small retrieve.py 7,472 B        sha256 3a3c03b05eb7b7a237bb46a617444bcaaab090b8e45bbfdb735c2770a49b6629
tiny  model.int8.onnx 18,850,806 B sha256 58de6db034bb4f6516b3bc1c68e7a0ac4e77abffe603a550ee87b39dcd0c7ac6
tiny  tokens.txt = same file as small (identical sha256)
HF repo revisions (api/models … "sha"): small 0c2744b9d70aaf45ba574c30c5d318c575258e66; tiny a291b33fd5a31cc4f212363ee1ba2d56d9710b49
HF rate limiting (429) encountered — fetch script retries with 45 s backoff; models did download on retry.
```
Archived copy: `third_party/` (git-ignored) + `scripts/fetch_models.py` re-downloads pinned
files and verifies hashes. **Permanent archive location = open question to Mo.**

### V-007 Silero VAD (source: sherpa-onnx releases tag `asr-models`)
```
asset silero_vad.onnx 0.64 MB — https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
sha256 9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6
```
Vad.kt config types verified in vendored source: `VadModelConfig(sileroVadModelConfig=
SileroVadModelConfig(model, threshold, minSilenceDuration, minSpeechDuration, windowSize,
maxSpeechDuration), sampleRate, numThreads, provider)` — matches VAD-CLOSED.

### V-008 Tanzil text (own measurement; source: tanzil.net download form)
```
POST https://tanzil.net/pub/download/index.php  quranType=uthmani&marks=true&sajdah=true&rub=true&outType=txt-2
→ 1,370,878 bytes, 6,266 lines: 30 header lines (copyright block) + 6,236 ayah lines `surah|ayah|text`
$ awk -F"|" '/^[0-9]+\|[0-9]+\|/{n++} END{print n}' → 6236   (spec §7.3 [VERIFY Q8] → CLOSED)
$ awk surah ids | sort -n | uniq | wc -l → 114
sha256 bf4f57b968d03f4131c070b1e285da9be0e0a108a21c910e872801ca273312c8
license block (in-file): CC-BY verbatim-only terms; notice must be carried in derived files (Q5-CLOSED).
Basmala check (own, script §7.3): 1:1 = بِسْمِ… present as ayah 1 of Al-Fatihah (not double-counted).
```
(Q8 part "ayah count = 6,236 under Hafs numbering" → verified against downloaded source.)

### V-009 Amiri font (source: aliftype/amiri GitHub releases)
```
releases.atom → latest tag 1.003 (2025-06-13 files inside the zip)
Amiri-1.003.zip 1,012,438 B sha256 81af0aff7d2086d8af24cea7202f7546130997982534691373485cd96744d05e
AmiriQuran.ttf 136,920 B  sha256 e2a47644762d16bdfb6d33e0d8db8c6ff30beae84150ef5a705316bbd829455c
OFL.txt (SIL OFL 1.1)      sha256 72de68e5954f4fdd24702292ef5a32f003ca960ec9330dc86e5eefb5dffb9b22
```
Shaping tests on API 26+ devices (§7.7): **pending** — no device/emulator in sandbox; moves to
Phase 2/3 with the first on-device run (flagged in STATUS).

### V-010 Gradle wrapper integrity
```
sha256sum gradle/wrapper/gradle-wrapper.jar → 621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8
matches services.gradle.org "checksumUrl … wrapperChecksum" for 9.9.2 (V-001).
gradlew downloaded from github gradle repo tag v9.9.2 (248 lines, official).
```

### V-011 Build gate (own measurement, sandbox: 2 cores / 4 GB RAM)
```
./gradlew assembleDebug → BUILD SUCCESSFUL in 42s (359 tasks) — all 15 modules
./gradlew :core:align:test → BUILD SUCCESSFUL (6 contract tests: identity, independence
   incl. the <50%-overlap and final-window rules, TrackerConfig defaults per §9.1)
Merged-manifest uses-permission set (release-merged check in CI):
   android.permission.RECORD_AUDIO (+ app-internal DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)
   NO INTERNET · NO ACCESS_NETWORK_STATE  (§4/§12.1 gate GREEN)
Debug APK: 84 MB (assets 55 MB + jniLibs 26 MB + code/resources; uncompressed debug build).
   Only arm64-v8a .so in APK (dependency ABIs stripped via packaging excludes — verified by jar -tf).
```

### V-012 S6 baseline GGML — BLOCKED
HF returned HTML error pages / 429 for `koribeche/wird-asr-model` tree+resolve during the
whole spike window (8 retries + direct CDN attempts). The file and its published SHA-256
(spec V6: `acc1f03c8b1468b674b4fab50e0b2312cff982ba0001cd16fe36ca23862b6536`) remain
un-verified locally. **Baseline is harness-only (§13.2) and not on the critical path**;
retry scheduled with the Phase 1 eval harness. Flagged in STATUS.

### V-013 Timestamp unit / frame shift (spec Q8) — PENDING
`OfflineRecognizer.kt` (vendored, v2.0.2) exposes `timestamps: FloatArray` with no documented
unit in source comments; sherpa convention is seconds. **Fixture confirmation requires a
decode on device/emulator → Phase 2.** The tracker is built to work in both modes
(§6.2 null-timing fallback; unit-tested in core:align).

## Pending verifications (carried to later phases)
| Item | Target phase |
|---|---|
| Fixture decode on API 26 emulator (S2 gate) | 2 |
| Timestamp unit/frame shift (Q8) | 2 |
| Amiri Quran shaping on API 26+ (§7.7) | 2/3 |
| RTF/RAM/battery/latency on real devices (Q7) | 2/4 |
| AudioSource effect size (Q6) | 2 |
| GGML baseline fetch+hash (S6) | 1 (with eval harness) |
| Release R8 build decode-on-device (§8.2) | 4 |
| AAB size ≤ 80 MB (§14) | 4 |

### V-014 Release (R8) build in sandbox — OOM-killed (own measurement)
`assembleRelease` with R8 minify: the Gradle daemon is kernel-OOM-killed during
`minifyReleaseWithR8`/`mergeDexRelease` on this 4 GB sandbox (dmesg: `oom-kill … task=java`),
even with heap lowered to 1280 m and parallelism off. Debug build + unit tests stay green.
Mitigation: `-PsandboxNoMinify` flag added (skips R8/shrink locally); **CI must run the full
minified release** (ubuntu-latest runners have more RAM) — encoded in ci.yml. R8 release
decode-on-device gate remains Phase 4.
