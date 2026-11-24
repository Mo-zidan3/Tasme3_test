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

### V-004 sherpa-onnx v2.0.2 assets (source: GitHub releases API + archive inspection) [SUPERSEDED by V-016]
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

### V-011 Build gate (own measurement, sandbox: 2 cores / 4 GB RAM) [SUPERSEDED by V-018; AAB MEASURED 55.9 MB, V-037]
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

### V-013 Timestamp unit / frame shift (spec Q8) — PENDING [SUPERSEDED by V-015]
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

## 2026-11-21 (later) — Phase 0.6 gate closure

### V-015 Q8 CLOSED on host — timestamp unit + frame shift (own measurement, host Python sherpa-onnx 2.0.2)
Setup: `pip install sherpa-onnx==2.0.2 soundfile numpy onnx` (host, not device). Fixture
synthesized locally (harmonic complex, 8 syllables/s, pitch steps; no gated audio, git-ignored,
never shipped): `/tmp/fixture3.wav` 8.0 s.
```
r = sherpa_onnx.OfflineRecognizer.from_zipformer_ctc(model="…/quran-asr-small/model.int8.onnx", tokens="…/tokens.txt", num_threads=2)
s = r.create_stream(); s.accept_waveform(16000, x); r.decode_stream(s)
result: 26 tokens; timestamps[:5] = 0.2, 0.28, 0.36, 0.48, 0.56 (seconds-scale values < 8.0 audio duration)
deltas_ms = [80,80,120,80,80,80,80,400,1520,1080,1440,440,1560,120,80,40,40,80,40,40,40,80,40,40,40]
histogram: 80ms×9, 40ms×8, 120ms×2 (+ speech-gap multiples) → min delta 40 ms; all deltas are multiples of 40 ms.
onnx metadata_props: model_type=zipformer2_ctc, vocab_size=31, subsampling_factor=4, comment="Quran recitation, Arabic letters without harakat (Warsh + Hafs)"
```
**Conclusion: timestamps are SECONDS; token frame shift = 40 ms (= 10 ms hop × subsampling 4).**
Confirms the §6.2 contract: `startMs = segmentStartMs + ts*1000`; `endMs = segmentStartMs + lastTs*1000 + 40`.
`core:align` `FRAME_SHIFT_MS = 40` constant + unit tests updated accordingly.
(Note: fixture is synthetic non-speech; it proves the timing grid, not recognition accuracy.)

### V-016 Native-lib compliance (own measurement, readelf on the shipped .so)
```
libsherpa-onnx-jni.so:
  LOAD segments: Align 0x4000 (all three)  → 16 KB page-size safe ✓
  NEEDED: libandroid.so, liblog.so, libonnxruntime.so, libm.so, libdl.so, libc.so
          → only libonnxruntime.so is our own extra; the two extracted .so are SUFFICIENT (V-004 closed)
  .note.android.ident: NDK r30 (rev 16248370), api_level 0x15 = min API 21
libonnxruntime.so:
  LOAD segments: Align 0x4000 (all four)  → 16 KB page-size safe ✓
  NEEDED: libdl.so, liblog.so, libm.so, libc.so (no our-own deps)
  .note.android.ident: NDK r27d (rev 13750724), api_level 0x1b = min API 27
```
Conclusions:
- **16 KB page-size compliance: YES** — all LOAD alignments are 0x4000 on both libraries.
  (Play's 16 KB page requirement targets apps *targeting* API 35+; our targetSdk 37 → must hold;
  measured directly, not assumed. Re-check at each jniLibs bump.)
- **Prebuilt minSdk: onnxruntime requires API ≥ 27, jni requires API ≥ 21** → effective native
  minSdk = **27**. Our declared minSdk 26 < 27: on API 26 devices the app may fail to load
  libonnxruntime.so. Escalation recorded in STATUS/DECISIONS (options: raise minSdk to 27 —
  clean, loses ~1% of devices; or accept 26 with a documented risk). NOT silently ignored.
- NEEDED confirms V-004's extraction choice (two .so suffice).

### V-017 Basmala embedding + font cmap coverage (own measurement, host) [SUPERSEDED by V-023]
Tanzil Uthmani (txt-2, marks+sajdah+rub):
```
awk -F'|' '$1==1&&$2==1' → 1|1|بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ      (Al-Fatihah 1:1 IS the basmala)
awk -F'|' '$1==2&&$2==1' → 2|1|بِسْمِ … ٱلرَّحِيمِ الٓمٓ                (basmala PREFIXED into ayah 1)
awk -F'|' '$1==9&&$2==1' → 9|1|بَرَآءَةٌ مِّنَ ٱللَّهِ …               (At-Tawbah: NO basmala) ✓
full-text==basmala ayahs: 1 (Al-Fatihah 1:1 only); basmala-prefixed ayah-1: 111 surahs (all but 1 and 9)
```
→ **N() and tracker must treat basmala as OPTIONAL at every surah start except Al-Fatihah (where it
is ayah 1, mandatory) and At-Tawbah (absent).** Expected-word sequences must strip the prefixed
basmala words from ayah-1 texts of surahs 2–8,10–114 or make them optional (never penalize).
Design note recorded for core:quran (Phase 1): store a `basmalaWords` marker, not removal.

Font coverage (fonttools, AmiriQuran.ttf 1.003):
```
distinct codepoints in text: 61 (29 letters + alef-wasla U+0671 + tatweel + harakat + Quranic marks)
marks-in-use: U+0640,0670,0671,064B–0654,06DC,06DF,06E0,06E2,06E3,06E5,06E6,06E7,06E8,06EA,06EB,06EC,06ED
AmiriQuran.ttf cmap (266 glyphs): 0 codepoints missing → FULL static coverage.
```
(Shaping/visual correctness still needs on-device render tests per §7.7 — not possible in sandbox.)

### V-018 Module count + size truth (own measurement)
```
settings.gradle.kts include() count = 13 Gradle modules:
  app, core:{quran,align,asr,audio,session,data,designsystem}, feature:{reader,recite,history,settings}, tools:eval
```
STATUS previously claimed "15-module" — WRONG; corrected to 13 (matches spec §6.1 tree; eval/scripts/docs
are directories, not Gradle modules).

Debug APK measured from the artifact (unzip/python zipfile, 2026-11-21):
```
group              compressed MB    %
assets/models           45.38      51.7%   (55.6 MB uncompressed: small 36.08 + tiny 18.85 + VAD 0.64)
lib arm64 .so           27.64      31.5%   (onnxruntime 22.25 + sherpa-jni 5.37 + 2 androidx stubs)
classes*.dex            14.07      16.0%   (debug dex; R8 release will shrink)
assets/notices           0.02
assets/fonts             0.08      (AmiriQuran.ttf 0.13 MB uncompressed)
res/ + root              0.57
TOTAL                   87.75 MB debug APK (uncompressed-ARSCLib; AAB differs — see below)
```
Debug APK ≠ download size. AAB download estimate: models 45.4 + jniLibs 27.6 stored as-is (Play
stores .so and assets with compression for download) + release dex (R8, est. 4–6 MB compressed,
not measured — no release build possible in sandbox, V-014) + res/root ≈ 0.6 →
**estimated AAB download ≈ 77–80 MB — AT the ≤80 MB budget edge.** This is an ESTIMATE, not a
measurement (release/AAB not built anywhere yet, E1). Options per §14 if over: PAD install-time
pack / drop tiny (−15.6 MB stored → ~62 MB, safe) / accept with ADR. Escalate to Mo now
(early, per Phase 0.6 instruction) rather than at Phase 4.

### V-019 Phase 0.6 T-task evidence (2026-11-21)
**T1 — CI has NOT run remotely.** Sandbox has no `gh` CLI and no git remote (`git remote -v`
empty). No way to trigger GitHub Actions from here. `assembleRelease` with R8 has still
never succeeded anywhere (V-014 sandbox OOM). → Phase 0 = **DONE WITH EXCEPTION E1**.
Mitigations committed: ci.yml now (a) runs fetch_native.py + generate_notices.py +
fetch_models.py (fully reproducible asset pipeline), (b) **release-minify guard** — fails CI
unless `app/build/outputs/mapping/release/mapping.txt` exists (proves R8 ran; closes the
sandboxNoMinify leak path), (c) merged-manifest offline check unchanged. First real CI run
must be triggered by Mo (push to GitHub). CI is expected to need ~50 min + model fetch time.

**T4 — Notices:** full license texts now ship in `app/src/main/assets/notices/` (generated by
`scripts/generate_notices.py`, pinned SHA-256 where available):
```
MIT-models.txt        sha256 3a9d79091d3c680c22dfa9af3dc8dfa12e62f6f9eb94bad36361393453a5583c (fetched at pinned rev 0c2744b…; earlier attempt got HTTP 429 ×4)
Apache-2.0.txt        sha256 cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30 (canonical apache.org)
OFL-1.1-AmiriQuran.txt sha256 72de68e5954f4fdd24702292ef5a32f003ca960ec9330dc86e5eefb5dffb9b22 (upstream 1.003 zip)
CC-BY-3.0-Tanzil.txt  byte-for-byte block from quran-uthmani.txt (diff-verified against source lines)
CC-BY-4.0-tlog.txt   sha256 9ba9550ad48438d0836ddab3da480b3b69ffa0aac7b7878b5a0039e7ab429411 (creativecommons.org)
README.txt           index + official BibTeX citation (Fadheli, Abdeladim; DOI 10.5281/zenodo.23761691)
```
Model copyright line, verbatim from LICENSE at the pinned revision: **"Copyright (c) 2026 Abdou"**
(model-card BibTeX author is Fadheli, Abdeladim — both recorded as-is, no adjudication).
Tanzil block (28 lines) also committed at `docs/tanzil-notice.txt` for derived-file headers.

**T5 — Reproducibility:** `scripts/fetch_native.py` (tarball SHA-256 verified; extracts ONLY
the arm64 pair; pins both .so hashes — measured: libonnxruntime.so
33847ad4…da1, libsherpa-onnx-jni.so 089fd48e…8084; VAD sha pinned). `jniLibs/` now git-ignored;
repo carries sources + fetch scripts only. README documents fetch order: fetch_native.py →
fetch_models.py → generate_notices.py → assembleDebug.

**T8 — STATUS rewritten** with honest states (DONE WITH EXCEPTION / PARTIAL) and the toolchain-risk
decision request for Mo.

### V-020 N() derivation + full-text verification (own measurement)
N() rules derived from the model repo's own normalizer (retrieve.py at pinned rev 0c2744b…):
_FOLD = {أ→ا, إ→ا, آ→ا, ٱ→ا, ٲ→ا, ٳ→ا, ى→ي, ئ→ي, ے→ي, ی→ي, ؤ→و, ة→ه, ک→ك}; _LETTERS = 29 letters;
every other char dropped (harakat, 0653–0655, dagger alif 0670, tatweel 0640, small waw/ya 06E5/06E6,
Quranic marks 06D6–06ED). Verified over the full Tanzil asset: 77,881 words → 327,514 letters,
exactly 29 distinct letters emitted, 0 words normalize empty; no tanween-split rejoin needed
(that rule targets the author's retrieve.py reference source, not Tanzil).
Golden tests (core:quran NormalizerTest, 12 tests incl. U+0670, 06DF, 06E5/06E6, alef-wasla, tatweel,
basmala = «بسم الله الرحمن الرحيم», ta'awwudh = «اعوذ بالله من الشيطن الرجيم») — GREEN.
Committed asset: core/quran/src/main/resources/quran-uthmani.txt (1,371,430 B) with the verbatim
Tanzil notice header; whole-file sha256 8c3a25b0ab5eff2fbb8762b10262caf2d350972c6e704c00eac21af47fa06751;
ayah-payload sha256 a0d9cd3755df1570c14a3a2c226b401ede4e4d12df44655f5873c0e1924bdd38.
Integrity tests (114/6236/sha/notice/basmala-9:1) — GREEN (21 tests in core:quran).

### V-021 core:align §9 implementation + tracker tests (own measurement)
- Semi-global NW with free leading/trailing OBSERVED and free LEADING expected skips; moves:
  match/substitute (cost 1−sim), skip-expected (gapMissed), extra-observed (gapExtra), 1↔2 merge.
- Tracker: identity dedup (idempotency), independence counting, interiority (timed + null-timing
  token-index mode), precedence machine (HINTED terminal > CORRECT never-downgraded > MISSED
  (pointer+3 & ≥2 independent absence) > WRONG (preset: Conservative ≥2 independent interior,
  Balanced single FINAL interior) > CLOSE > PENDING), EXTRA dedup (text,250ms bucket), speech-only
  lost timer, range-wide reanchor (≥3 consecutive τ_correct), hint, summary per §9.6.
- 31 unit tests GREEN (table + property): incl. boundary-exclusion on partials vs pause-final
  relaxation, null-timing interiority, monotonic pointer, repeats→EXTRA, merge/split, evidence
  precedence, WRONG presets, HINTED terminal, MISSED partial-sequence confirmation.
- Two real bugs found & fixed during testing (recorded): (1) absence evidence was able to trigger
  WRONG (a skip is not a misrecitation) — absence now counts only toward MISSED; (2) an
  all-EXTRA observation (repeat behind window / ta'awwudh) was discarded as unusable — now
  records EXTRA stats and feeds the lost timer.

### V-022 tools:eval + eval/run_asr.py synthetic run (own measurement) [SUPERSEDED by V-025]
Pipeline exercised end-to-end: N()(surah 112 = 19 words) → run_asr.py --synthetic → hypotheses.json
(3 clips: correct/skip/wrong-planted) → tools:eval (same core:align as the app) → report.
```
clip syn_correct_001: correct=16 close=0 wrong=0 missed=0 hinted=0 extra=3 | صحيحة: 16 من 19
clip syn_skip_002: correct=15 close=1 wrong=0 missed=0 hinted=0 extra=3 | صحيحة: 15 من 19
clip syn_wrong_003: correct=1 close=1 wrong=0 missed=0 hinted=0 extra=0 | صحيحة: 1 من 19
```
All labeled "not measured on real audio". Interpretation (harness-realism gaps, documented in the
report): whole-clip finals vs partial streams; single-final skip (no ≥2 confirmations); 800 ms
synthetic windows vs 2×boundaryMs. Real decode path (run_asr.py on actual clips) is ready and
exercised the pinned model earlier (V-015 fixture decode used the same sherpa-onnx API).

### V-023 Basmala reconciliation CLOSED — 111 was a counting bug in the CHECK, not the text (2026-11-21)
Enumeration (letter-level first word of ayah 1, marks stripped):
```
112 surahs (2–8, 10–114) have basmala PREFIXED into ayah 1:
  110 with plain بِسْمِ, and TWO with بِّسْمِ (shadda+kasra under ba): surah 95 (At-Tin) and 97 (Al-Qadr).
  Raw lines:
  95|1|بِّسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ وَٱلتِّينِ وَٱلزَّيْتُونِ
  97|1|بِّسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ إِنَّآ أَنزَلْنَٰهُ فِى لَيْلَةِ ٱلْقَدْرِ
surah 1: ayah 1 IS the basmala (بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ, byte-exact per V-020 golden test)
surah 9: NO basmala (بَرَآءَةٌ مِّنَ ٱللَّهِ …)
```
The earlier "111" (V-017) used a byte-`startswith("بِسْمِ")` check: it missed the two shadda variants
(95, 97) and coincidentally counted surah 1 through a different mark order — net 111. The Tanzil
text itself is CORRECT: 114 = 112 prefixed + 1 Fatihah + 1 Tawbah exactly.
**Parser verdict: no bug.** The `بِّسْمِ` (U+0628 U+0651 U+0650…) spelling is the known Uthmani
rasm variant of the basmala's first word in those two surahs.
Modeling (§7.6): derived expected-words table tags the prefixed basmala of the 112 non-Fatihah
surahs as `OPTIONAL_PREFIX` — never counted in Y, never MISSED, absorbed when recited. Fatihah's
basmala is a MANDATORY word (it is ayah 1).

### V-024 N() collision analysis (own measurement, host; eval/n_collision_analysis.json)
Full-text sweep (N() over all 77,881 words):
```
distinct normalized forms: 14,636
forms mapping to >1 distinct original word: 2,771 (18.9% of forms)
word instances involved in collisions: 53,629 / 77,881 (68.9%)
top collisions: من ← {مِن, مَن, مِّن, …17 variants}; الله ← {ٱللَّهِ, ٱللَّهُ, ٱللَّهَ};
                 ان ← {إِنَّ, أَن, إِن, أَنَّ…}; في ← {فِى, فِىٓ, فِّى}; ما ← {مَا, مَّا, مَآ}; …
ayahs identical to another ayah after N(): 275 ayahs in 96 groups (whole Quran; e.g. the 6
  basmala+الم openers 2:1=3:1=29:1=30:1=31:1=32:1; 2:5=31:5; 2:47=2:122; 2:134=2:141; 2:162=3:88)
ayah pairs identical after N() within a single surah: 94
```
Conclusion: this is an INHERENT property of the letters-only model token space (V-020), not a
normalization bug. Two different Quranic words that share a normalized form give sim=1.0 —
a real substitution between them is UNDETECTABLE in the app's comparison space. Documented in
docs/EVALUATION.md + user-facing Limitations text (not "fixed" by inventing rules — §0 hard rule).

### V-025 SegmenterSimulator + trailing-EXTRA verdict + finals-lo fix (own measurement)
Built `SegmenterSimulator` (core:align/sim) — ONE simulator, TWO consumers (core:audio tests +
tools:eval generation): session-global ms; partialWindowMs=2500 @ partialEveryMs=1000;
pause-finals 500 ms; maxSegmentMs=8000 with overlapMs=1000; isFinal flags; optional VAD padding.

**Trailing-EXTRA investigation (P2): REAL ALIGNER DEFECT FOUND AND FIXED.**
Root cause chain (probe-measured, evidence dumps in test history):
1. The old synthetic report's trailing-EXTRA was partly a generator artifact (single whole-clip
   final), AND partly a real defect: `alignWindow` aligned EVERY observation against
   `[pointer−backWords, pointer+lookahead]` — for a FINAL covering a whole segment, the pointer
   had already advanced past the segment start, so the segment's early words could only become
   "free leading OBSERVED" (no evidence) → trailing words never reached CORRECT and substituted
   words never accumulated final-interior votes.
2. FIX: FINALS now align with lo=0 (leading expected skips are free since V-021; no cost
   distortion). Measured after fix: 19/19 words CORRECT on the app-shaped stream (was 16/19);
   MISSED fires for a skipped word with its slot silent; WRONG fires for a persistent
   substitution (كين for يكن, sim 0.333) under BOTH presets.
3. CORRECTED STRUCTURAL CLAIM (my earlier hypothesis disproved by measurement): the FINAL is
   itself an independent interior observation (§9.2: final + windows differ), so a substitution
   interior in one partial + the final = 2 independent votes → Conservative-WRONG fires within
   a segment. The earlier رحيم case stayed CLOSE only because a MERGE pushed bestSim to exactly
   tauWrong (0.5) — a threshold boundary. 1-letter slips on 2–3-letter words (sim ≥ 0.5)
   legitimately produce CLOSE.
4. Pathological-vocabulary lesson: synthetic words like w0..w18 (every pair sim=0.5) sit exactly
   at tauWrong — meaningless. The simulator tests use real Quranic words (surah 112) now.
Tests: 35 GREEN in core:align (incl. 4 simulator tests; cadence/width/truncation asserted
within-segment 1000 ms, width ≤ 2500+2×vadPad, first partials truncated at segment start — app
behavior).

### V-026 §7.5 N() self-validation — PRIMARY model, clean studio clips (own measurement)
Fixtures (public EveryAyah MP3s of Abdul Basit Murattal 192 kbps — a studio-quality reciter,
clean close-mic): converted to 16 kHz mono, git-ignored (`eval/fixtures/`), never shipped.
Scoring: model space (N(expected) vs N(hypothesis)), CER per clip, primary model only:
```
1:1   audio=4.4s CER=0.00%  ref="بسم الله الرحمن الرحيم"                 hyp identical
1:3   audio=4.0s CER=0.00%  ref="الرحمن الرحيم"                          hyp identical
1:5   audio=5.6s CER=0.00%  ref="اياك نعبد واياك نستعين"                 hyp identical
112:1 audio=3.2s CER=0.00%* ref="قل هو الله احد" (basmala EXCLUDED)      hyp identical
112:4 audio=4.3s CER=0.00%  ref="ولم يكن له كفوا احد"                    hyp identical
114:6 audio=5.1s CER=0.00%  ref="من الجنه والناس"                         hyp identical
AGGREGATE CER = 0.00% (target <2%) → §7.5 N() self-validation PASSES on clean fixtures.
```
*112:1 first scored 62% against a reference that INCLUDED the prefixed basmala — the reciter
legitimately omitted it. With the basmala treated as OPTIONAL (§7.6, V-023 OPTIONAL_PREFIX),
CER = 0.00%. The optional-prefix modeling is REQUIRED even for evaluation, not just tracking.
Caveats (honest): 6 clips, ONE reciter (studio) — indicative on clean audio, NOT the §13 phone-mic
gate; author-reported raw-space numbers were NOT used. Provenance/license note: EveryAyah audio
compilation © EveryAyah (CC-BY-4.0 per their site; individual recording rights belong to the
reciter/heirs) — fixtures used locally for validation only, never committed, never shipped.

### V-027 S6 baseline — STILL BLOCKED; run_baseline.py stub lands (2026-11-21)
Retried twice more this session (90 s + 180 s backoff): HTTP 429 then a 404 on the second URL
form. The whisper.cpp GGML remains un-fetchable. `eval/run_baseline.py` now exists with the
§13.2 interface and REFUSES to produce numbers ("baseline NOT YET MEASURED"). Harness-only;
never blocks Phase 2.


### V-028 Record hygiene + supersessions (2026-11-22)
Superseded entries (kept for history, marked here normatively):
- V-004 (jniLibs "x86_64 also present" + minSdk guess 24/26) → **superseded by V-016** (floor 27).
- V-011 (15 modules / ≈84 MB estimate) → **superseded by V-018** (13 modules / 77–80 MB estimate;
  both estimates to be replaced by the bundletool measurement from the first CI run).
- V-013 (host timestamps probe) → **superseded by V-015** (seconds + 40 ms grid, golden tests).
- V-017 ("111 basmalas") → **superseded by V-023** (112; the 111 was a byte-startswith artifact).
- V-022 (synthetic report v1 metrics) → **superseded by V-025** (app-shaped streams; lo=0 fix).
- D-025 → renumbered **D-013**.

New host evidence (2026-11-22):
- check_abi_floor.py over jniLibs: arm64 onnxruntime api_level 27, sherpa-jni 21; x86_64
  onnxruntime 27, sherpa-jni 21 → max=27 == minSdk 27 (both source sets).
- make_timing_fixture.py determinism: two runs → identical payload sha256
  da6798fb059d326d486c86994d2c912113fb6e2ef04bfda1310339236e37ec43.
- THIRD_PARTY_NOTICES.md Tanzil block now byte-verbatim vs the committed asset (25/25 lines;
  trailing spaces included); MIT copyright corrected to the fetched LICENSE bytes
  ("Copyright (c) 2026 Abdou").
- fetch_native.py: tarball member paths begin "./jniLibs/…" — extraction filter fixed
  (previous startswith never matched; masked by the sidecar skip).
- bundletool pinned 1.17.0 (sha256 printed at fetch time into the CI log for the record).

### V-029 JaCoCo re-measured after Phase-2 host-side (2026-11-22, exec-file analysis)
Re-run after the new tests (basmala OPTIONAL_PREFIX, simulator, scheduler, assembly, session):
```
core:quran  LINES covered=67  missed=5  total=72   → 93.1%   (prev 93.6% — real re-measure)
core:align  LINES covered=372 missed=37 total=409 → 91.0%   (prev 91.7% — real re-measure)
core:audio / core:asr / core:session: android-library modules — jacoco wiring lands with the
  CI coverage step (their tests are testDebugUnitTest; html reports exist per-module but are
  not aggregated host-side this session).
```
The two docs quotes (93.6/91.7) were pre-Phase-2 copies; the re-measured numbers above are the
current truth and are what STATUS must quote.

### V-030 PUSH COMPLETE — first CI run triggered; monitoring blocked by token scope (2026-11-22)
```
git remote add origin https://github.com/Mo-zidan3/Tasme3_test.git   (token NEVER in URL)
GIT_ASKPASS=/tmp/ap.sh git ls-remote origin   → exit 0 (auth OK; remote had ZERO refs)
GIT_ASKPASS=/tmp/ap.sh git push -u origin main → "* [new branch] main -> main"
```
- Leak gates BEFORE and AFTER: repo content clean (no token-shaped string: the scanner's own
  regex literals excluded by real-shape matching), .git/config clean (no credential in URL),
  /tmp/ap.sh removed immediately.
- Workflow confirmed on the remote via contents API: .github/workflows/ci.yml present, 15,849 B.
- The push touched code+workflow (non-docs paths) → the workflow TRIGGERED (push event on main).
- **Monitoring BLOCKED**: the fine-grained token lacks the Actions permission —
  `GET /actions/runs` → 403 "Resource not accessible by personal access token"; same for
  check-runs, commit statuses, deployments, and workflow_dispatch (403 each). Public HTML
  actions page → 404 (repo private). Per §3's rule ("If Actions read is denied, ask Mo to paste
  the logs — do not guess outcomes"): NO gate is marked green from the workflow file alone.
  E1/AAB/S2/minSdk-on-device remain PENDING Mo pasting the run logs/URLs.
- What the run will produce when Mo shares it (per-job evidence targets listed in docs/CI.md):
  secrets-scan + notices check, build (unit tests/manifest/classpath/ABI floor debug),
  release (R8 guard + arm64-only guard + floor), size (bundletool SDK/ABI/density), instrumented
  API 27+35 (loadLibrary, generated-fixture timing decode, font smoke).

### V-031 §4 host-side fixes demanded by the measurements (2026-11-22)
1. **Simulated latency p95 4700 → 950 ms** (p50 750 → 550). Two root causes, both fixed and
   re-measured: (a) EvalMain's latency metric used the FIRST occurrence's endMs for duplicate-
   text words (الله ×3, احد ×2 in surah 112) — a metric bug fabricating multi-second latencies;
   (b) partial windows truncated at segment start (both the Kotlin SegmenterSimulator and
   run_asr.py's `_stream`) — now drawn from the session ring buffer across segment boundaries.
2. **EXTRA inflation 6–7/clip → 0**: dedup key (text, startMs/40) [fallback (text, windowEndMs)]
   + suppression of EXTRA for audio positions previously MATCHED. The 6–7 were re-covered matched
   audio behind the pointer, not user repeats.
3. **Repeated identical ayahs (surah 55 refrain ×31 normalized-identical; surah 77 ×10)**:
   3 new invariants tested (pointer monotonic on earlier identical refrain; nearest-forward
   re-anchor; segment-initial skip → MISSED). REAL DEFECT FOUND+FIXED: free leading expected
   skips in lo=0 FINALS recorded NO absence evidence → segment-initial skips could never reach
   MISSED. Now every skipped k < spanMin in a final records absence (independent of interiority;
   §9.3's pointer≥3 gate still applies at state level).
4. **strictOverlapIndependence flag** (default OFF): independence(a,b,strict) — strict mode
   ignores the isFinal clause. Measured: WRONG fires 1/1 (default) vs 0/1 (strict) on the same
   كين substitution stream. Default kept per V-025; numbers to Mo before the §13 gate.
Tests: core:align 39 GREEN (35 + 3 repeated-ayah + 1 independence-rule); full suite 92 green.

### V-035 CI repair run 45035800627 harvested; repair set authored (2026-11-22)
Run `45035800627` (commit 3b1da0a, pushed by Mo after adopting the V-033 draft fixes in
7d6979b/9984281). Per-job evidence from the real job pages (the six named log files were NOT
present in upload/ — evidence harvested from the public GitHub pages, logged-out view):
- **secrets-scan GREEN**: credential scan + notices placeholder check both pass (the V-033
  '…' self-match fix is live).
- **build GREEN**: assembleDebug, unit tests, :app:lintDebug, merged-manifest offline check,
  classpath hygiene, ABI-floor check, debug ABI contents — all steps success.
- **release GREEN**: assembleRelease + bundleRelease SUCCEEDED with R8 (mapping.txt present);
  release APK = arm64-v8a only; release-outputs uploaded. **E1's "never built minified
  release/AAB" exception is CLOSED — first minified release + AAB built remotely.**
- **size RED — RACE**: "Artifact not found for name: release-outputs". The size job began
  before the release job's artifact upload existed; no `needs:` ordering and the two jobs run
  concurrently. CI defect, not a product defect. bundletool never ran: **AAB download size
  remains UNKNOWN** (77–80 MB stays an ESTIMATE, V-018).
- **instrumented API 27 + 35 RED — no KVM**: both died at "Run instrumented tests on emulator"
  after "Timeout waiting for emulator to boot." (per Mo's pasted step log: ubuntu-26.04 images
  carry no /dev/kvm; the emulator refuses `-accel off` with "Software CPU emulation is not
  supported. A hardware CPU accelerator is required"). Fetch + fixture-generation steps were
  GREEN — the failures are purely boot/acceleration. CI defect, not a product defect.
  **S2-on-device stays OPEN.**

**REGRESSION found in Mo's 3b1da0a (web-editor rewrite of generate_notices.py)**: on a cold
runner the rewritten fallbacks produce DEGRADED license texts — cold-clone reproduction:
Tanzil notice 1004 B (canonical 1106 B / sha256 72493509…), Amiri OFL 4027 B (canonical
4389 B / sha256 72de68e5…), and the rich tracked README.txt (citation + limitations) is
overwritten with a shorter form. The GREEN build/release jobs in 45035800627 therefore
shipped degraded license assets. Repaired in this session's commit set (see below).

**Repair set (this session; single commit)**:
1. ci.yml P0 — the size job is DELETED; the bundletool measurement is merged into the
   release job AFTER bundleRelease (option (b): saves a full job setup, removes the race by
   construction). bundletool 1.17.0 pinned by sha256 54ebee1f…; in-job debug keystore
   (never committed); `build-apks --mode=universal` + `get-size total
   --dimensions=SDK,ABI,SCREEN_DENSITY`; prints to $GITHUB_STEP_SUMMARY + size-report.txt;
   ≤80 MB target warn / >85 MB hard fail (§14) kept. size-report.txt joins release-outputs.
2. ci.yml P1 — instrumented: runs-on ubuntu-24.04 (26.04 has no /dev/kvm); explicit
   pre-flight `test -e /dev/kvm || FAIL` + `sudo chmod 666 /dev/kvm`;
   `disable-linux-hw-accel: false`; `-no-metrics` in emulator-options;
   emulator-boot-timeout 900; matrix API 27+35 kept, arch x86_64. NO TCG fallback (would
   need Mo's explicit acceptance of very long runs).
3. ci.yml P2 — quality gates made REAL: `|| echo`/`|| true` removed; ktlint 1.8.0 CLI
   (pinned sha256 a3fd6202…) hard-fails on parse errors and ratchets violations against the
   committed baseline 817 (measured this session; grows = FAIL); detekt 1.23.8 wired at the
   root build (applied to all subprojects — the "Task 'detekt' not found" fake pass is gone)
   with maxIssues ceiling 100000 pending Mo's ratchet decision; JaCoCo gate parses the
   produced XML and hard-fails below 85% line coverage on core:quran/core:align
   (scripts/ci_coverage_gate.py); the coverage numbers printed are the measured ones.
4. generate_notices.py P0b — cold-cache fallbacks now reproduce the canonical license
   texts BYTE-EXACT: Tanzil from the committed quran-uthmani.txt resource (1106 B), OFL
   from the committed asset (4389 B), README kept as tracked (only generated when absent).
   Verified by idempotent cold run: exit 0, `git status` clean on assets, shas match
   72493509…/72de68e5….
5. KDoc parse fix — SegmenterSimulator.kt "[i..j]" → "(i..j)" (ktlint parser false-positive
   on the bracket token; the file always compiled).
6. CI.md fixture wording aligned: the TIMING fixture (eval/fixtures/timing_fixture.wav —
   generated by the workflow, read by the timing test) vs the §0-gated ACCURACY clip
   (eval/fixtures/fixture.wav, Mo's own recording, still absent by design).

**Local-verification caveat (honest)**: `./gradlew detekt` could NOT be executed in this
sandbox — repo.maven.apache.org 429-rate-limits this IP (same class as V-012/V-027), which
breaks Gradle plugin resolution at config time (exact error captured: "could not resolve
plugin artifact 'org.jetbrains.kotlin.jvm:...2.4.21'"). CI runners are unaffected — the
pushed run itself verifies. ktlint/coverage-gate/notices steps were verified locally.

### V-036 review fixes applied; commit split A/B; license-bytes gate added (2026-11-22)
Four review fixes on the pending V-035 repair (f2d20ab), then split:
1. **ci_coverage_gate.py** — JaCoCo parsing fixed to REPORT-LEVEL counters only
   (`root.findall("counter")`, direct children of `<report>`). The previous
   `root.iter("counter")` summed method+class+sourcefile+package+report LINE counters and
   inflated printed counts ~5×. Synthetic nested-XML test: fixed parser returns (2,3) where
   the old returned (8,12). The 85% floor is unchanged.
2. **generate_notices.py** — copy-canonical, never reconstruct: the shipped Tanzil/OFL
   notices are COPIED from the git-tracked canonical assets; the committed
   core/quran resource header block is the bootstrap-only source (byte-asserted;
   `next()` → safe `(…, None)` lookup, no StopIteration on header drift). All six canonical
   SHA-256s pinned in-script with a FINAL gate loop; hard-fail on any mismatch. Tested:
   happy path exit 0 (all gates OK), degraded bytes exit 1 ("NON-CANONICAL LICENSE BYTES"),
   first-boot Tanzil bootstrap reproduces 1106 B exactly (sha 72493509…), missing-source
   OFL fails loudly rather than inventing text.
3. **ktlint** — magic number 817 replaced by ktlint's OWN baseline:
   `ktlint-baseline.xml` committed (36 files; generated with the pinned CLI 1.8.0,
   sha256 a3fd6202…). CI: parse errors hard-fail; violations OUTSIDE the baseline fail.
   Negative test: a planted `val x=1` in a new file fails the run (exit 1) while the
   baseline'd debt passes (exit 0).
4. **build.gradle.kts split** — the V-035 root wiring (untyped
   `extensions.configure("detekt") { setProperty(…) }`, never compiled — sandbox Maven
   429) is REMOVED from the pushed commit. Commit A = workflow/scripts/docs/baseline/KDoc
   only (no root build-script change → cannot kill the green release job at configuration).
   Commit B = typed `extensions.configure<DetektExtension>` wiring + config/detekt.yml +
   the `./gradlew detekt` CI step; HELD until `./gradlew help` compiles locally
   (Maven Central 429 permitting) — then pushed separately.

**§3 NEW GATE — license BYTES, not ellipsis**: run 45035800627's GREEN release shipped
NON-CANONICAL notices (every job printed "Using preserved Tanzil fallback block";
CC-BY-3.0-Tanzil.txt 1004 B vs canonical 1106 B; OFL sha cb4bf64b… vs canonical 72de68e5…),
and secrets-scan passed them because it only greps for '…'. Fixes: (a) generate_notices.py's
final gate loop asserts every notice SHA-256 vs the pinned canonical (exit 1 on drift);
(b) NEW scripts/check_notices_sha.py — standalone canonical-SHA gate, wired as an explicit
release-job step BEFORE assembleRelease (tested: happy exit 0 / degraded exit 1). This
record supersedes the V-035 "regression repaired" note with the concrete CI-side gate.

### V-037 CI run 45061168092 (commit a0e5021) — instrumented GREEN; size MEASURED (2026-11-22)
First run with the V-035 repair set + V-036 review fixes. Per-job evidence (API + logs +
artifacts; token used via GIT_ASKPASS, never in URL/disk; leak gates clean before/after):
- **secrets-scan GREEN**.
- **instrumented (27) GREEN + instrumented (35) GREEN** — the P1 repair WORKED:
  `ls -l /dev/kvm` → `crw-rw-rw- 1 root kvm` (KVM present on ubuntu-24.04; the pre-flight
  passed), emulator booted in ~37 s (no `-accel off` refusal), `:app:connectedDebugAndroidTest`
  ran to BUILD SUCCESSFUL in 2m30s on both API levels.
  Per-test outcomes (from the androidTest-results artifacts):
  - `loadLibrarySmoke_bothNativeLibsLoad` **PASS on API 27 AND 35** — onnxruntime +
    sherpa-onnx-jni native libs LOAD on the API-27 floor. **D-012's minSdk=27 decision now has
    on-device proof** (runtime API-27 evidence, not just .note.android.ident).
  - `amiriQuranFont_rendersAllTanzilCodepoints_nonEmpty` **PASS on both** — the font asset
    loads on-device and renders the Tanzil codepoints non-empty.
  - `sherpaAccuracyDecode_moClip_loudSkipWhenAbsent` SKIPPED (loud, by design — Mo's clip
    still absent, §0 gate intact).
  - `sherpaTimingDecode_generatedFixture_secondsAnd40msGrid` **SKIPPED — DEFECT FOUND**:
    the test looks for `eval/fixtures/timing_fixture.wav` via repo paths (cwd/../), but the
    DEVICE cannot see the runner's repo checkout; the workflow generates the fixture on the
    host, never pushes it to the device. Fixed in 15cf4ab: the runner script now
    `adb push`es the fixture to the app's external-files dir
    (/sdcard/Android/data/com.iqraa.tasmee/files/…) and repoFile() probes that path +
    getExternalFilesDir(). NEXT run is the first with a real on-device decode assertion.
- **release RED (measurement SUCCEEDED, gate step crashed)**: bundletool 1.17.0 (sha-verified)
  ran `build-apks --mode=universal` (in-job debug keystore) and `get-size total
  --dimensions=SDK,ABI,SCREEN_DENSITY` printed `27-,,,55900674,55900674` — **MEASURED AAB
  universal download size = 55,900,674 B ≈ 55.9 MB (min=max), BELOW the 80 MB target and the
  85 MB ceiling**. The §14 budget is MET with margin (V-018's 77–80 MB estimate superseded by
  the real measurement; no PAD / drop-tiny / ADR escalation needed). The step then crashed on
    a parsing bug (see below) — the measurement itself is valid and recorded here.
  The notices canonical-SHA gate step PASSED (release job reached the bundletool step =
  generate_notices + check_notices_sha both exit 0 → shipped notices are canonical bytes).
- **build RED at the coverage gate**: `[FAIL] core:quran/core:align: report missing` —
  `jacocoTestReport` ran (:core:quran FROM-CACHE, :core:align executed) but NO XML existed on
  disk: the JVM jacoco plugin under Gradle 9 did not produce the XML (xml.required default
  false for this setup), so the gate correctly hard-failed on missing reports (no fake pass —
  the gate does its job). Fixed in 15cf4ab: `xml.required.set(true)` + pinned
  `reports/jacoco/test/jacocoTestReport.xml` output location in BOTH module builds; the gate
  now also probes the two known XML layouts.
- **ktlint step**: skipped (build failed before it); committed baseline + parse-error gates
  unexercised this run — exercised next run.

**Parsing defect fixed (release)**: the size-step's regex `([\d,]+)` matched the bare comma
between `MIN` and `MAX` header fields → `int('')` ValueError. Replaced by a proper CSV parse
(takes the last two numeric columns as MIN/MAX; verified against the exact run output →
55.9 MB / 55.9 MB). The ≤80 MB warn / >85 MB fail semantics unchanged.

### V-037b FULL GREEN RUN 45064204429 + follow-ups (2026-11-22, commits 6c6ca1a→833f4ec)
Run `45064204429` (6c6ca1a): **ALL FIVE JOBS GREEN** — secrets-scan, build, release,
instrumented (27), instrumented (35). This is the first fully-green pipeline in the project's
history. Measured evidence harvested from logs + artifacts:
- **Coverage gate (REAL numbers, report-level counters)**: `core:quran 67/72 lines = 93.1%`,
  `core:align 383/421 lines = 91.0%` — both above the 85% floor, PASS. (V-029's numbers
  confirmed live: 93.1% matches; align 91.0% on 383/421 vs the doc's 372/409 — the module
  grew 12 lines with the test additions and coverage held.)
- **ktlint**: `violations OUTSIDE the committed baseline: 0` — PASS with the regenerated
  baseline (ktlint's own baseline format; parse errors would fail).
- **AAB download size (bundletool 1.17.0, universal apks, CSV-parsed)**:
  `SDK,ABI,SCREEN_DENSITY,MIN,MAX` → `27-,,,55900663,55900663`;
  **measured MIN 55.9 MB / MAX 55.9 MB — §14 budget MET with margin** (target 80 MB, ceiling
  85 MB). V-018's 77–80 MB ESTIMATE is RETIRED; no PAD/drop-tiny/ADR escalation needed.
  (55,900,663 B this build vs 55,900,674 B in run 45061168092 — 11 B of zip-timestamp noise.)
- **Notices canonical-SHA gates**: all six `[gate] … OK` in BOTH generate_notices (final
  in-script gate) and the release-job step (check_notices_sha.py) — the shipped license bytes
  are byte-exact canonical (Tanzil 1106 B/72493509…, OFL 4389 B/72de68e5…, MIT 1062 B/3a9d7909…,
  Apache 11358 B/cfc7749b…, tlog 18657 B/9ba9550a…, README 1815 B/c67401e8…).
- **Instrumented API 27 (all pass)**: `loadLibrarySmoke_bothNativeLibsLoad` PASS (0.001s —
  minSdk=27 native floor, on-device); `amiriQuranFont…` PASS (0.005s);
  **`sherpaTimingDecode_generatedFixture_secondsAnd40msGrid` PASS in 0.617–0.724s — the
  S2 timing contract is now proven ON-DEVICE: real sherpa JNI decode of the pushed fixture,
  seconds-scale timestamps, 40 ms frame-grid deltas.** Accuracy test loud-skips (Mo's clip
  still absent — §0 intact).
- **Instrumented API 35**: loadLibrary + font PASS; timing test SKIPPED — the fixture
  (pushed to the app's external-files dir, confirmed `1 file pushed`) is not visible to the
  app under scoped storage on Android 15 from the probed paths (API 27 sees the same path
  via legacy storage). Follow-ups: push to the instrumentation (.test) dir too, probe both
  contexts + canonical /storage/emulated/0 path, add shell `ls` + `run-as` diagnostics, and
  the skip message now embeds the probed paths (833f4ec — the next run yields device-side
  truth). NOT a decode defect: the same fixture decoded on API 27 and host.
- Fix history this session (all verified by run output): jacoco XML production (typed
  `named<JacocoReport>` + `xml.required` + pinned path — two blind DSL typos caught by runs
  45061936775/45062446816 and fixed); CSV size parser (int('') crash on the header comma);
  fixture generator upgraded to V-015's syllabic-envelope + pitch-step signal class (the
  continuous stack decoded to ZERO words — API-27 run 45063204700; host-verified with the
  pinned model: 5 tokens, ts ≤ dur+40 ms, deltas exact 40 ms multiples).

**S2 status: the timing-decode half of S2 is CLOSED on API 27 (on-device, measured);
API-35 parity pending the visibility diagnostics. minSdk=27 runtime proof: CLOSED
(loadLibrarySmoke PASS on API 27 AND 35, runs 45061168092/45064204429/45065160094).**

**Commit B (detekt) remains HELD**: the sandbox's Maven Central 429 (V-035/V-037) blocks
`./gradlew help` compile verification of the root build-script change. Per the review
instruction (never couple the evidence-gathering pipeline to an unverified build-script
change), B waits for a local compile window; D-017 remains open for Mo.

### V-037c API-35 parity CLOSED — full green incl. timing decode on BOTH APIs (2026-11-22, run 45067719380)
Root cause of the API-35 skip (probed-path evidence from run 45066011428): scoped storage on
Android 15 hides shell-pushed files under /sdcard/Android/data from the app's File probes —
even its OWN getExternalFilesDir path stat'd false — while API 27 saw the same path via legacy
storage. Two more defects were found and fixed along the way:
- instrumentation-context cacheDir gave `Permission denied` on File.createTempFile (run
  45066936654, both APIs) → temp file now in the TARGET context's cacheDir.
- Final channel: the fixture rides INSIDE the androidTest APK assets (CI generates it into
  app/src/androidTest/assets/eval/fixtures/ — gitignored, deterministic; repoFile() copies it
  out via assets.open → target-cacheDir temp file; null-safe for Mo's absent accuracy clip).

**Run 45067719380 (commit 177b71e): ALL FIVE JOBS GREEN. Per-test outcomes (artifacts):**
- API 27: loadLibrarySmoke PASS; amiriQuranFont PASS; **sherpaTimingDecode PASS (0.597s)**;
  accuracy clip loud-skip (§0 intact).
- API 35: loadLibrarySmoke PASS; amiriQuranFont PASS; **sherpaTimingDecode PASS (0.598s)**;
  accuracy clip loud-skip.
**S2 (on-device decode: JNI path + timing contract) is now CLOSED on BOTH API 27 and API 35.**
Q8's timing contract (seconds-scale timestamps, 40 ms frame grid) holds on-device at both
floors; minSdk=27 native-floor proof (loadLibrary) holds at both. The only remaining skip is
Mo's own accuracy clip (§0 by design).

### V-038 T4 — Mo's clips: authorization of record, committed fixture, real-audio harness (2026-11-22)
Authorization: Mo uploaded the 6 clips with "Mo states these recitations are permitted for
use" — recorded verbatim in eval/data/PROVENANCE.md (the authorization of record).
- Clips identified (decode + best-F1 ayah-window matching): 39:42–43 (Ezz El-Awami, ayah 42
  repeated), 19:4–7, 5:23, 54:10–12, 25:27–31 (Riad Mostaf ×4), 2:200–201 (Yasser
  Al-Dossary, hadr-fast). **Classification: 3 speakers × PROFESSIONAL STUDIO recitations
  (clean AAC, high SNR) — §13 phone-gate criteria NOT met (needs ≥60 held-out clips,
  ≥3 speakers on PHONE MICS). Every number below is INDICATIVE; studio recitations do not
  satisfy the phone gate. WRONG stays rendered as CLOSE in the app.**
- Committed to git: exactly ONE clip — eval/data/fixture_54_10_riadmostaf.wav (10.05 s,
  16 kHz mono PCM, al-Qamar 54:10, host decode CER 0.0% letters-only) + PROVENANCE.md.
  All other audio git-ignored (eval/data/* whitelist). No gated/tlog audio anywhere.
- androidTest `sherpaAccuracyDecode_moClip…` no longer skips in CI: the workflow copies the
  committed fixture into the androidTest APK assets (same V-037c channel), and the test now
  asserts the 54:10 letters-only CER ≤ 0.20 on-device (host measured 0.0%).
- Real-audio harness (scripts/t4_real_audio_report.py; quran-asr-small; model space N()
  = letters-only + hamza folding; full-clip offline decode — NOT the app's 8 s streaming
  segmentation, so long clips penalize both sides; honest limitation recorded):
  ```
  ezz_elawami_39_42     75.4s WER 25.0% CER 19.3% (repeated ayah → hyp word loss)
  riadmostaf_19_4_5     68.8s WER  4.3% CER  1.1%
  riadmostaf_5_23       41.0s WER  0.0% CER  0.0% (perfect)
  riadmostaf_54_10_12   38.0s WER  5.3% CER  1.2%
  riadmostaf_25_27_29   70.6s WER  8.3% CER  5.2%
  yasser_2_200_201      32.1s WER 42.1% CER 34.4% (hadr-fast; decode drops words: hyp 27 vs ref 38)
  macro WER 14.2% / macro CER 10.2%; micro WER 15.5%
  clip-level false-alarm rate 6.2% (12/192 hyp word occurrences, bag-level)
  clip-level detection rate 84.5% (180/213 ref word occurrences)
  defect clips (CER>0): 5/6 — rule-of-three NOT applicable (defects observed)
  ```
- Honest reading: on CLEAN STUDIO audio the model is strong (4/6 clips ≤ 8.3% WER), the two
  outliers are explained by (a) ayah repetition (Ezz) and (b) hadr-fast pace (Al-Dossary) —
  the model's weak spot on fast recitation, which the §13 phone gate must re-test on real
  hardware. FA 6.2% is bag-level, NOT the app's session EXTRA rate. These numbers do NOT
  certify phone-mic behavior (INDICATIVE label, subset + scoring space stated).

### V-039 T4+T5 verified on-device — run 45079074846 (21c93ca): ALL 5 JOBS GREEN (2026-11-22)
Instrumented per-test outcomes (artifacts, both floors, 5 tests each):
- **`sherpaAccuracyDecode_moClip…` PASS on API 27 (0.473 s) AND API 35 (0.670 s) — NO SKIP**:
  the committed fixture (al-Qamar 54:10, PROVENANCE-recorded permission) decodes on-device
  and the letters-only CER ≤ 0.20 assertion HOLDS on both floors. The accuracy test is no
  longer a §0 skip — it is a real, passing, licensed-fixture assertion.
- `sherpaTimingDecode…` PASS (0.389 s / 0.621 s); `loadLibrarySmoke` PASS; font smoke PASS.
- **T5 PERF SMOKE (INDICATIVE — EMULATOR x86_64, NOT a phone; Q7 OPEN for Mo's hardware)**,
  from the job log's logcat harvest (IqraaPerfSmoke tag):
  ```
  API 27: load=311 ms decode=170 ms audio=8000 ms RTF=0.021 coldStartToFirstDecode=481 ms
  API 35: load=461 ms decode=224 ms audio=8000 ms RTF=0.028 coldStartToFirstDecode=685 ms
  peakRSS: dumpsys TOTAL-PSS parse returned n/a on this run (parser widened in the follow-up
  commit — next run reports the value; NOT a measured number, NOT claimed)
  ```
  Labels: synthetic non-speech fixture (timing/8 s generated) — proves the JNI path + timing
  grid + load/decode latency, NOT recognition accuracy; emulator x86_64 with KVM, not a phone.

### V-040 T6a–e verified — run 45080772312 (7312881) ALL FIVE JOBS GREEN (2026-11-22)
Phase-3 first slice on CI, all jobs green:
- **build**: ktlint 0 out-of-baseline (globs fixed to core/*/src — 'core/**' matched
  build/generated/BuildConfig.kt producing 2150 phantom violations in run 45079846042);
  detekt ≤200 ceiling green (T3 ratchet); coverage gate unchanged (93.1/91.0).
- **instrumented API 27 + 35**: all tests green — loadLibrary, timing decode, font,
  accuracy (54:10 fixture), perf smoke, **RoomDbTest dao_roundTrip_checkpoint_and_history +
  dao_delete_all PASS on-device** (§15 Room v1 schema + checkpoint transaction + history Flow).
- **T5 perf numbers this run (API 27): load=375 ms, decode=229 ms, RTF=0.029,
  coldStartToFirstDecode=604 ms — INDICATIVE EMULATOR x86_64, NOT a phone; Q7 OPEN.**
  peakRSS: dumpsys from the instrumentation process does not return the TOTAL line
  (non-shell uid) — honestly labeled "TOTAL-line-not-found", NOT a measured number.
- Defects found & fixed by runs this session: ReciteScreen stripped imports (compile fail
  45080315624 → restored getValue/setValue/delay + coroutines dep); RoomDbTest first()
  extension misuse (Unresolved 'first', 45079846042); ktlint glob over build/ dirs.
- Phase-3 landing state: core:data (Room+DataStore keys+repository), designsystem
  (§10.2 FeedbackEncoding color+glyph+decoration, M3 theme dark/RTL, font scale 0.8–2.0,
  QuranText annotated rendering, Amiri runtime font), SessionLifecycle §10.4 machine + 5
  host tests, full §10/§11 strings ar+en (Arabic UNREVIEWED until Mo signs off), screens
  F1 reader / F2-F3 recite (auto-scroll pause chip + tasmee-only hint) / F5 history / F6
  settings, MainActivity + NavHost routes.

### V-041 PR-1 — refrain anchoring + per-word independence + .env hygiene (2026-11-23)
Branch fix/align-v041. Evidence: the CI PR run (build+release+secrets-scan per the T3a PR
policy; instrumented re-runs on main after merge) — numbers recorded from the run logs, not
from local execution (sandbox 429 blocks Gradle; CI is the verifier).

Defects fixed (all three of Mo's verified defects):
1. **Refrain/repeated-ayah false alarms:** FINALs aligned lo=0 with an EARLIEST-end tie-break,
   so a refrain-only pause-final matched the FIRST copy of the refrain (identical after N());
   the actually-recited copy starved, and unguarded leading-skip absence then flooded the
   PENDING words — Mo's measured repro string CCCCCCMMMMCMMMMCLLLLCLL.. (25 words).
   Fix: D-020 items 1+2. AFTER (CI test `T1 refrain-only pause-finals - all 25 words CORRECT`):
   all 25 CORRECT, missed=wrong=close=0, pointer=25.
2. **Observation-level independence:** `any { independent(...) }` accepted a vote independent
   of ANY previous window, so mutually-overlapping partials each re-voted. Fix: D-020 item 5.
   AFTER (CI tests T2a/T2b): systematic misdecode stays CLOSE with independent count = 1 in
   BOTH flag modes; a genuinely independent window still reaches WRONG (count = 2) in both
   modes. Single-window recognizer deletion stays CORRECT (T2c).
3. **Secret hygiene:** `.env` was tracked from the initial commit (299b869) despite the
   `.env*` ignore. Untracked (git rm --cached; contents never printed anywhere); `.env.example`
   placeholders added; CI now blocks tracked `.env*` and generic secret shapes; scanner
   verified happy+negative locally before push. Rotation advice to Mo in the PR text.

Invariants preserved (existing tests, unmodified): I1 = V-025
`TRAILING-EXTRA question settled` (19/19 CORRECT) and `MISSED fires` — green in the PR run;
I2 = V-031 `segment-initial skip still produces MISSED` — green; I3 = V-031
`WRONG fires under default rule and is suppressed under strict overlap` (1/0) — green.
New tests: RefrainOnlyFinalTest (T1/T1b), SystematicMisdecodeTest (T2a/b/c),
PerfectRecitationSurahTest (T3: surahs 1, 55 [31 refrains], 77 [10], 112 through the REAL
SegmenterSimulator; basmala recited/omitted — OPTIONAL_PREFIX never penalized).
ALL results SYNTHETIC (simulator streams, not real audio); the §13 phone-mic gate
(≥60 held-out correct clips, ≥3 speakers, phone mic, FA ≤5%) remains NOT met — WRONG still
renders as CLOSE in the app.

### V-042 Stage B (T7/T8/T9) — PRs #4/#5/#6, stacked (2026-11-24)
CI is the verifier (§0: local Gradle stays 429-blocked). Merge order T7 → T8 → T9; each PR
run tests the cumulative tree. Evidence quoted from the runs' logs; anything still in flight
at report time is labeled PENDING, never claimed green.
- **T7 (PR #4, feat/t7-pipeline)**: real AudioCapture (AudioRecord 16k mono PCM16→float,
  VOICE_RECOGNITION, MIC_UNAVAILABLE on mic-taken/revoked — never crash); host-testable
  PipelineEngine (VAD predicate → real Segmenter → 12s session ring → ReciteSession) + 2
  host tests (fake recognizer kept); ReciteViewModel on the §10.4 lifecycle (pause releases
  the mic immediately; resume after 1 s countdown; resync → session.reanchor). Instrumented:
  micCapture_smoke + pipeline_endToEnd_fixture (54:10 clip through REAL silero VAD +
  Segmenter + SherpaOnnxRecognizer + tracker). Defects caught by CI runs and fixed:
  missing viewModelScope import (45297626868/45298125430), JUnit5 assertion argument order
  (45299033107). Final state: run with head 20b382f PENDING at report time.
- **T8 (PR #5, feat/t8-mvp-features, rebased onto T7)**: F7 OnboardingScreen (rationale +
  riwaya + limitations + denied-permanently deep link), F8 AboutScreen (shipped notices
  rendered from assets, Tanzil attribution + tanzil.net via browser intent, Amiri OFL),
  §9.6 SummaryScreen (tappable flagged list, §10.2 encoding), §10.6 ResyncPickerDialog
  (ayah picker within range), §11 ModelLoadErrorCard, §10.5 DegradedBadge; onboarding-first
  NavHost. Defect caught: branched off main without T7's unmerged deps (45298201476) →
  rebased onto T7's tip.
- **T9 (PR #6, feat/t9-ui-tests, stacked on T8)**: ReciteUiTest (tasmee-only hint §10.3,
  pause banner, glyph-assertable §10.2 encoding), MainActivityUiTest (F7 grant flow,
  rotation survival, 2.0× font scale), WCAG AA ContrastTest COMPUTED for the §10.2 palette
  on light AND dark surfaces (measured failures 4.42/4.24 fixed via darkened light shades +
  new theme-adapted dark variants through FeedbackEncoding.adapted: light 5.9–6.6:1, dark
  8.1–11.0:1); manual script M1–M5 EXECUTED in CI (scripts/manual_ui_script.sh after the
  instrumented tests; evidence in the instrumented-api* artifact); H1–H4 hardware steps
  honestly OPEN in docs/TESTING.md — **Phase 3 NOT claimed DONE until Mo executes them**.

### V-043 Stage B (T11) — app-shaped §13 harness (PR #7, stacked on T9) (2026-11-24)
[Superseeded-by note, V-044: the word-union WER numbers quoted below were measured with a
SCORING BUG and are RETIRED — see the V-043 text; the tracker-replay per-word states are
the metric. Kept for the record, never quote as a result.]
- scripts/t11_app_shaped_eval.py: silero VAD (the SHIPPED silero_vad.onnx weights driven
  via onnxruntime directly — the pip sherpa-onnx 2.0.2 python VoiceActivityDetector
  produced ZERO speech segments on every feed pattern tried (whole/chunked/512-aligned,
  with flush; 4 attempts, documented in the script)) → the Kotlin Segmenter law (partials
  1000/2500 ms, pause-final 500 ms, force-cut 8 s + 1 s overlap, VAD pad 300 ms) → EACH
  WINDOW'S AUDIO decoded separately → session-global timings.
- 12 hypothesis files (6 clips × whole-clip/app-shaped) + 6 expected files committed as
  DERIVED TEXT (word lists + timings; no audio, nothing gated — the V-038 authorization
  covers evaluation use).
- Scoring metric: the REAL RecitationTracker's per-word states via tools:eval replay in CI
  (scripts/t11_replay.sh). An initial local word-union WER (309% "app-shaped") was a
  SCORING BUG (overlapping partials re-cover audio by design — the tracker's per-word
  evidence handles that) and is explicitly NOT the metric; recorded here so the number
  never gets quoted.
- Whole-clip baseline (V-038, unchanged): macro WER 14.2% / CER 10.2%.
- ALL labels INDICATIVE: studio clips; §13 phone gate NOT met; WRONG renders CLOSE.

### V-044 Stage B landed — the chain, every run, and the defects CI caught (2026-11-24)
Merge order executed with per-run evidence (PR runs: build+release+secrets-scan; instrumented
runs only on main — the known stacked-PR blind spot, which is exactly where three of the
defects below hid):
- **PR #4 (T7)** run 45300215711 GREEN → merged 6485744. Main run 45333042145 caught
  `runBlocking` used as a RETURN TYPE (the PR never compiles androidTest) → hotfix 4baeb6a;
  style follow-ups e20aac3→d6af35e (lease-guarded amend of my own 2-min-old commit — noted
  deviation, no Mo commits touched).
- **PR #5 (T8)** run 45335602074 GREEN → merged 95a25e0.
- **PR #6 (T9)** — CI caught: junit-platform-launcher missing for designsystem unit tests
  (45333188692), the versionless compose ui-test artifacts needing the BOM on
  androidTest/debug configurations (45335531056/45335602431 — the earlier "metaspace OOM"
  read was WRONG; corrected in the commit message), and my experimental
  detekt.yml style-section made per-module detekt fail (45336527102, :core:align:detekt 352
  weighted issues) → reverted; the D-017 ratchet stays untouched. Run 45339236533 GREEN →
  merged ae98c79. Main run 45339976550 caught the missing MainActivity import in
  MainActivityUiTest (30f2416).
- **PR #7 (T11)** run 45339952042 GREEN **with the replay FATAL and real** (the prior run
  45339239201 had gone green while all 12 replays silently failed — non-fatal || echo; fixed
  and recorded) → merged 4c8c71b.

### V-045 Two REAL pipeline defects found by landing the stack (2026-11-24)
1. **Word-separator mismatch (CRITICAL, found by the E2E instrumented test, run
   45337588118):** the bundled quran-asr vocab has NO "▁" token — it separates words with
   the SPACE token (tokens.txt: `<blk>`, letters, `" "`). The Kotlin assembler split on "▁"
   only ⇒ EVERY live decode produced ONE GIANT WORD — the app's word-level follow-along
   would have been broken in production. Masked twice: host WordAssemblyTest used synthetic
   ▁ streams, and eval CER was computed after N() which strips spaces. Fix (main 24e8f2c):
   assembler accepts BOTH separators + host regression test with the real token shape; the
   E2E fixture test (all-CORRECT/CLOSE assertion) is the on-device guard. Same bug existed
   in the T11 python emitter (its first replay's correct=0-1/missed-heavy numbers were that
   harness artifact — RETIRED, never quote).
2. **Reanchor crash with zero matches (found by the E2E stream, run 45336537914):**
   `tryReanchorRangeWide` called `matches.last()` on an empty list (a global alignment can
   yield zero Match moves) → NoSuchElementException on both API levels. Fix (main a57f3f9):
   early return + host regression test.

### V-045b THE REAL T11 metric — tracker replay, Mo's 6 studio clips (run 45339952042, PR #7)
INDICATIVE (3 speakers, studio; §13 phone gate NOT met; WRONG renders CLOSE). Per-word
states through the REAL RecitationTracker (correct/close/wrong/missed; whole-clip vs
app-shaped; hit-rate = correct+close over expected):

| clip | whole C/L/W/M | app C/L/W/M | app hit-rate |
|---|---|---|---|
| ezz_39_42 (40w) | 16/0/0/0 | 35/5/0/0 | 100% |
| riad_19_4_7 (47w) | 16/0/0/0 | 46/1/0/0 | 100% |
| riad_25_27_31 (48w) | 12/4/0/0 | 42/4/0/2 | 95.8% |
| riad_54_10_12 (19w) | 15/1/0/0 | 14/3/0/2 | 89.5% |
| riad_5_23 (21w) | 16/0/0/0 | 3/5/0/10 | 38.1% |
| yasser_2_200_201 (38w) | 6/10/0/0 | 21/6/1/10 | 71.1% |
| **TOTAL (213w)** | **81/15/0/0** | **161/24/1/24** | **86.9%** |

Reading (honest): the app-shaped stream beats the single whole-clip final on 5/6 clips
(86.9% tracked vs 45.1%) — partials+finals give interior evidence a lone final cannot
(V-025's insight, now measured on real audio). NO systematic MISSED/CLOSE collapse; two
anomalies stay OPEN for investigation before Phase 4: (a) riadmostaf_5_23 — silero VAD
fragments this clip into 11 finals (21 words) and the app mode falls to 38.1% while
whole-clip holds 76.2% (VAD threshold/segmentation on this reciter); (b) yasser (known
worst decoder accuracy of the set) loses 10 words. The 309% word-union number from the
first local attempt remains RETIRED (V-043).

### V-046 T11 anomaly root cause — riadmostaf_5_23's 38.1% is a REAL per-window-context defect (2026-11-24)
Run 45340839779 (main, 4c8c71b) re-replayed all 12 T11 files green. The 5_23 app-shaped
regression (38.1% hit-rate, missed=10, extra=16 vs whole-clip decode WER 0%/CER 1% per
V-038) was investigated from the COMMITTED hypothesis JSON — not papered over:
- The clip is pause-heavy: silero VAD (100 ms decisions) + the §6.2 Segmenter law cut ELEVEN
  pause-finals (≥500 ms silences) across a 37 s recitation of 21 words — speech bursts of
  0.7–4.3 s. partials=7 only.
- Isolated decode of those tiny windows COLLAPSES: the finals decode «طه» (not even in
  surah 5:23 — a hallucinated surah opener on an 876 ms window), «فاموزمون» (a CTC merge of
  «فإنكم غالبون»), «يلدولم يولد» (merged 112-vocabulary), «بيف», «فتعكلون». The same audio
  decodes near-perfectly as ONE whole clip (V-038) — the model NEEDS acoustic context.
- The tracker behaved CORRECTLY on what it was given: words never decoded right never
  matched → the guarded absence rules fired MISSED (10); the hallucinations surfaced as
  EXTRA (16). NOT a tracker bug, NOT a harness artifact.
- Comparison set: yasser_2_200_201's 71.1% is MODEL-limited (whole-clip decode itself only
  got 27/38 words; app-mode tracked every decodable word — 27/38); the other four clips
  (89.5–100%) have 33–58 partials whose 2.5 s trailing windows carry enough context.
- Phase-4 candidate mitigations for MO (product calls — NO threshold change made, I4):
  (a) widen pause-final windows with trailing context like the partials (window SHAPE, not
  §9 policy); (b) carry decoder state/context across force-cuts; (c) minimum-speech-length
  before a pause-final is emitted. All three change latency behavior — Mo decides.
- Same run: 3 instrumented test bugs (all mine, fixed in 016c3e8) — the grantFlow assertion
  used the en app_name while the reader renders the hardcoded Arabic «اقرأ»; fontScale2 set
  content on an already-populated activity rule (moved to a component rule); the tasmee
  test called setContent twice on one rule (split). Plus 1 ktlint import-order violation
  from the 30f2416 hotfix. The T11 replay step itself ran 12/12 green in that run.

### V-045c CLOSED — releaseSmoke green on main; the R8 fix chain, every run (2026-11-24)
The androidTest-APK R8 loop closed on commit e5e3f42; **main run 45370776920 ALL FIVE JOBS
GREEN** (build 138364174819, release 138364174769, instrumented-27 138364174694,
instrumented-35 138364174815, secrets-scan 138364174231 — ids from the run's job list).

**Decode proof (the point of T10):** the R8-MINIFIED, debug-signed releaseSmoke APK decoded
the committed 54:10 fixture ON DEVICE, both emulators, from the job logs:
- API 27: `IqraaReleaseSmoke: decoded 5 words (R8 build)` (job 138364174694 log)
- API 35: `IqraaReleaseSmoke: decoded 5 words (R8 build)` (job 138364174815 log)
5 = فدعا ربه اني مغلوب فانتصر. Minify stayed ON throughout — every failure below was fixed
in keep/suppression config, never by disabling R8.

The chain the smoke test forced (each = a real run, red→green):
1. 45365479136 (main, 9e1642ac) RED — connectedReleaseSmokeAndroidTest task-not-found →
   ef136a2: variant-agnostic `connectedAndroidTest` + `-PiqraaTestBuildType=releaseSmoke`.
2. 45366457648 RED — backslash continuation became a literal gradle arg ("Task '\' not
   found") → d2f91c0: single-line invocation.
3. 45367329394 RED — `minifyReleaseSmokeAndroidTestWithR8` MISSING class
   `javax.lang.model.element.Modifier` (errorprone compile-only annotations, absent on
   Android; referenced from IncompatibleModifiers.value()) → 9edc9e7: `-dontwarn`
   suppression. This is EXACTLY the class of bug T10 exists to catch before Play.
4. 45368930517 RED — the app-side -dontwarn never reached the TEST APK's minification:
   its R8 config reads `testProguardFiles`, not `proguardFiles` → 05bd7a9: suppressions in
   smoke-test-proguard.pro wired via buildType.testProguardFiles.
5. 45369846137 RED — AGP 9 exposes `testProguardFiles` as a `val MutableList<File>`; the
   `+= "path"` string form hit the reassignment path (val reassign + type mismatch) →
   e5e3f42: append `File(...)` elements.
6. 45370776920 GREEN — see above. §8.2 "release decodes on device" risk CLOSED on emulator;
   a real-phone decode remains part of H4/Q7 (phone gate), still OPEN.

### V-047 V-046 candidate (a) landed + the Mo phone kit (PR #10) (2026-11-24)
**The fix (D-021, Mo-approved):** PipelineEngine widens a pause-FINAL whose speech spans
< `minDecodeWindowMs` (new named config, default 2500 ms = the partial window shape):
trailing context first (the pause's own silence — fire time is exactly 500 ms into the
pause, so the CTC right context is the elapsed pause), then leading (the previous segment
tail), clamped to the session ring; the OBSERVATION's word-evidence bounds stay the HONEST
segmenter bounds, so §9.2 interiority discounts context words and NO §9 threshold, preset,
or default changed (I4). Forced-cut finals never widen; 0 disables.
ReciteSession.onWindow gained `decodeStartMs` (default = the observation start, unchanged
path). scripts/t11_app_shaped_eval.py mirrors the law for regeneration.

**Honest T11 replay scope:** the committed hypotheses are INPUTS recorded before the
change, and Mo's audio is GONE from the sandbox (regeneration is PROVENANCE-gated, §0) —
so the replay CANNOT show the 5_23 improvement, and no number is invented for it. What the
green replay DOES prove: zero tracker-level regression — run 45458565154's replay totals
are IDENTICAL to V-045b per clip (5_23 app stays 3/5/0/10; yasser 21/6/1/10; the five
healthy clips unchanged). The real-audio before/after comes from regeneration with Mo's
audio or from a live session on Mo's phone.

**The run chain (PR #10, branch fix/v-046-context):**
1. 45455422224 RED — 2 of the 4 new host tests failed: MY expected values were wrong
   (assumed the final fires 600–700 ms into the pause; the law fires at exactly 500 ms —
   cursor = speechEnd + 500, trail = the elapsed pause, lead fills the deficit). The
   IMPLEMENTATION was correct; test arithmetic fixed from the run's own report.
2. 45457254107 RED — ktlint: 5 new violations (when-entry bracing, function-signature).
   Fixed in code; the baseline is UNTOUCHED (rule: fix debt, never hide it). Verified
   locally with the CI-pinned ktlint 1.8.0 (sha a3fd6202…): 0 outside the baseline.
3. 45458565154 GREEN — build 138674535439, release 138674535514, secrets-scan
   138674535137 (instrumented PR-skipped by policy): unit tests incl. the 4 new V-046
   tests, T11 replay, coverage, ktlint, detekt, lint, manifest-offline, classpath, ABI
   floor all green.

**The phone kit (T1/T2):** the release job now uploads the `app-release-smoke.apk`
artifact (app + androidTest APKs, R8 ON, debug-signed, arm64-checked; PR-run artifact
id 14952421462) and the job summary prints the download URL with the TESTING-ONLY
warning. docs/PHONE_TEST.md is the non-technical H1–H3 checklist (observed/pass-fail
columns); scripts/phone_perf.sh measures REAL-HARDWARE cold start, load/RTF/RSS, the R8
decode proof, and a live 60 s session → eval/reports/phone-<device>.md; Q7 closes only
when that file exists and is pasted back.

**T4 prune:** minSdk 27 (ratified, runtime-proven, D-022) and AAB 55.9 MB (budget MET,
D-023) removed from Needs-from-Mo — 6 genuinely open items remain.

### V-047b CLOSED on main — all five jobs green; the 429 detour, honestly recorded (2026-11-24)
Main run **45459779954** (merge commit ceb3907): build, secrets-scan, instrumented 27/35
ALL GREEN (releaseSmoke decoded 5 words on both APIs) — but the **release job failed on
`error_prone_annotations` resolution: "Received status code 429" from repo.maven.apache.org**
(the V-006/V-012 transient class; the same deps resolved fine in PR runs 45458565154/
45459650777). The PAT cannot re-run jobs (403) — so the fix landed as 51cd8d4: the
releaseSmoke APK assembly step now carries the same 60/120/240/480 s backoff the pinned
asset fetches already use (retry, never skip, still hard-fails loudly).

**Main run 45461715778 (51cd8d4): ALL FIVE JOBS GREEN** — build 138685760838, release
138685761527, secrets-scan 138685761318, instrumented-27 138685761437, instrumented-35
138685761363. Decode proof re-confirmed on device: `IqraaReleaseSmoke: decoded 5 words
(R8 build)` in both instrumented job logs. The T11 replay totals match V-045b identically
(zero tracker-level regression). The phone-kit artifact `app-release-smoke.apk`
(id 14953945423, ~58 MB: app + androidTest APKs + README) is on this run.
