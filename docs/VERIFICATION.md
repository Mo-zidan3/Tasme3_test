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

### V-017 Basmala embedding + font cmap coverage (own measurement, host)
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

### V-022 tools:eval + eval/run_asr.py synthetic run (own measurement)
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
    (/sdcard/Android/data/com.example.tasmee/files/…) and repoFile() probes that path +
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
